import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { messageErreur, messageErreurBlob, telechargerFichier } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '../components/ui/table';

/** Taille de page fixe : un registre se feuillette, il ne s'épuise pas. */
const TAILLE_PAGE = 25;

const vide = { recherche: '', categorie: '', initiative: '', du: '', au: '' };

/** Lit les critères depuis l'URL (vue partageable, fil du navigateur). */
const lireDepuisUrl = (params) => ({
  recherche: params.get('recherche') ?? '',
  categorie: params.get('categorie') ?? '',
  initiative: params.get('initiative') ?? '',
  du: params.get('du') ?? '',
  au: params.get('au') ?? '',
  page: Math.max(0, parseInt(params.get('page') ?? '0', 10) || 0)
});

const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleDateString(langue, { day: '2-digit', month: 'short', year: 'numeric' });

const formaterMontant = (valeur, langue) =>
  new Intl.NumberFormat(langue, { style: 'currency', currency: 'MAD', maximumFractionDigits: 0 })
    .format(Number(valeur ?? 0));

/**
 * Registre global des dépenses : information financière — lecture ET
 * écriture réservées à DEPENSE_SAISIR (20), le serveur refuse aux autres
 * (la route protège aussi GET /api/initiatives/{id}/depenses).
 *
 * Filtres recherche / catégorie / initiative / période, total des dépenses
 * filtrées (en-tête X-Total-Montant du serveur), pagination et export CSV
 * complet. L'URL porte les critères : la vue filtrée se partage (« Voir
 * les dépenses » d'une fiche).
 */
export default function DepensesPage() {
  const { t, langue } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();

  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [lignes, setLignes] = useState([]);
  const [total, setTotal] = useState(0);
  const [totalMontant, setTotalMontant] = useState(null);
  const [initiatives, setInitiatives] = useState([]);
  const [categories, setCategories] = useState([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');

  // Listes déroulantes (filtres) : initiatives + catégories déjà saisies.
  useEffect(() => {
    api.get('/initiatives?size=200')
      .then((res) => setInitiatives(res.data ?? []))
      .catch((err) => setErreur(messageErreur(err)));
    api.get('/depenses?size=200')
      .then((res) => {
        const uniques = [...new Set((res.data ?? []).map((d) => d.categorie).filter(Boolean))];
        setCategories(uniques.sort());
      })
      .catch(() => { /* la page de dépenses est rechargée juste après */ });
  }, []);

  // Toute modification de l'URL recharge le registre.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    charger(depuisUrl);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const construireParams = (f, options = {}) => {
    const params = new URLSearchParams();
    if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
    if (f.categorie) params.set('categorie', f.categorie);
    if (f.initiative) params.set('initiative', f.initiative);
    if (f.du) params.set('du', f.du);
    if (f.au) params.set('au', f.au);
    const page = options.page ?? 0;
    if (page > 0) params.set('page', String(page));
    return params;
  };

  const charger = async (f) => {
    setChargement(true);
    setErreur('');
    try {
      const params = construireParams(f, { page: f.page });
      params.set('size', String(TAILLE_PAGE));
      const res = await api.get(`/depenses?${params.toString()}`);
      setLignes(res.data ?? []);
      const compte = parseInt(res.headers['x-total-count'] ?? '', 10);
      setTotal(Number.isNaN(compte) ? (res.data?.length ?? 0) : compte);
      const montant = res.headers['x-total-montant'];
      setTotalMontant(montant === undefined ? null : montant);
    } catch (err) {
      setErreur(messageErreur(err));
      setLignes([]);
      setTotalMontant(null);
    } finally {
      setChargement(false);
    }
  };

  const appliquerFiltres = (e) => {
    e.preventDefault();
    // Une nouvelle recherche repart à la première page.
    setSearchParams(construireParams(filtres), { replace: true });
  };

  const reinitialiser = () => {
    setFiltres({ ...vide, page: 0 });
    setSearchParams({}, { replace: true });
  };

  const allerPage = (page) => {
    setSearchParams(construireParams(filtres, { page }), { replace: true });
  };

  const exporterCsv = async () => {
    setErreur('');
    try {
      await telechargerFichier(
        `/depenses/export?${construireParams(filtres).toString()}`,
        'depenses.csv'
      );
    } catch (err) {
      setErreur(await messageErreurBlob(err));
    }
  };

  const pagesTotales = Math.max(1, Math.ceil(total / TAILLE_PAGE));
  const depart = total === 0 ? 0 : filtres.page * TAILLE_PAGE + 1;
  const arrivee = Math.min((filtres.page + 1) * TAILLE_PAGE, total);
  const filtresActifs = Boolean(
    filtres.recherche.trim() || filtres.categorie || filtres.initiative || filtres.du || filtres.au
  );

  const champ = (nom) => (e) => setFiltres((f) => ({ ...f, [nom]: e.target.value }));

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('depenses.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('depenses.sousTitre')}
          </p>
        </div>
        <Button
          type="button"
          variant="secondary"
          onClick={exporterCsv}
          disabled={lignes.length === 0}
          title={t('depenses.exportAide')}
        >
          {t('depenses.exportCsv')}
        </Button>
      </header>

      {erreur && (
        <div className="mb-4 rounded-md border border-destructive/50 bg-destructive/50 p-3 text-sm text-destructive">
          {erreur}
        </div>
      )}

      <Card className="mb-6 p-[18px] shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold text-muted-foreground">
            {t('depenses.filtres')}
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-0">
          <form className="flex flex-wrap items-start gap-3" onSubmit={appliquerFiltres}>
            <div className="flex-1 min-w-[180px] space-y-2">
              <Label htmlFor="depenseRecherche">
                {t('depenses.recherche')}
              </Label>
              <Input
                id="depenseRecherche"
                type="search"
                placeholder={t('depenses.recherche')}
                aria-label={t('depenses.recherche')}
                value={filtres.recherche}
                onChange={champ('recherche')}
              />
            </div>
            <div className="flex-1 min-w-[160px] space-y-2">
              <Label className="text-xs font-medium text-muted-foreground">
                {t('depenses.filtreCategorie')}
              </Label>
              <select
                aria-label={t('depenses.filtreCategorie')}
                value={filtres.categorie}
                onChange={champ('categorie')}
                className="min-w-[160px] rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">{t('depenses.toutesCategories')}</option>
                {categories.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
            <div className="flex-1 min-w-[160px] space-y-2">
              <Label className="text-xs font-medium text-muted-foreground">
                {t('depenses.colInitiative')}
              </Label>
              <select
                aria-label={t('depenses.colInitiative')}
                value={filtres.initiative}
                onChange={champ('initiative')}
                className="min-w-[160px] rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">{t('depenses.toutesInitiatives')}</option>
                {initiatives.map((i) => (
                  <option key={i.idInitiative} value={i.idInitiative}>{i.code}</option>
                ))}
              </select>
            </div>
            <div className="flex-none space-y-2">
              <Label className="text-xs font-medium text-muted-foreground whitespace-nowrap">
                {t('depenses.periodeDu')}
              </Label>
              <input
                type="date"
                aria-label={t('depenses.periodeDu')}
                title={t('depenses.periodeDu')}
                value={filtres.du}
                onChange={champ('du')}
                className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </div>
            <span className="text-muted-foreground whitespace-nowrap mt-6.5" aria-hidden="true">
              →
            </span>
            <div className="flex-none space-y-2">
              <Label className="text-xs font-medium text-muted-foreground whitespace-nowrap">
                {t('depenses.periodeAu')}
              </Label>
              <input
                type="date"
                aria-label={t('depenses.periodeAu')}
                title={t('depenses.periodeAu')}
                value={filtres.au}
                onChange={champ('au')}
                className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </div>
            <div className="flex items-end gap-2">
              <Button type="submit" disabled={chargement}>
                {t('depenses.appliquer')}
              </Button>
              <Button type="button" variant="secondary" onClick={reinitialiser}>
                {t('depenses.reinitialiser')}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card className="mb-6 p-[18px] shadow-sm">
        <div className="mb-3.5 flex items-center justify-between gap-3">
          <span className="text-sm text-muted-foreground">
            {total > 0 ? t('depenses.compteur', { de: depart, a: arrivee, total }) : ''}
            {totalMontant !== null && (
              <strong className="depenses-total">
                {' · '}
                {t('depenses.total', { montant: formaterMontant(totalMontant, langue) })}
              </strong>
            )}
            {filtresActifs && (
              <>
                {' · '}
                <button
                  type="button"
                  className="text-primary underline underline-offset-2 hover:text-primary/80"
                  onClick={reinitialiser}
                >
                  {t('depenses.reinitialiser')}
                </button>
              </>
            )}
          </span>
        </div>

        {chargement ? (
          <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">
            {t('common.chargement')}
          </div>
        ) : lignes.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            {t('depenses.aucune')}
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('depenses.colDate')}</TableHead>
                <TableHead>{t('depenses.colInitiative')}</TableHead>
                <TableHead>{t('depenses.colLibelle')}</TableHead>
                <TableHead className="text-right">{t('depenses.colMontant')}</TableHead>
                <TableHead>{t('depenses.colCategorie')}</TableHead>
                <TableHead>{t('depenses.colSaisisseur')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lignes.map((d) => (
                <TableRow key={d.idDepense}>
                  <TableCell>{formaterDate(d.dateDepense, langue)}</TableCell>
                  <TableCell>
                    <Link to={`/initiatives/${d.idInitiative}`} className="text-primary hover:underline">
                      {d.codeInitiative}
                    </Link>
                    <span className="ml-1 text-xs text-muted-foreground">
                      {d.nomInitiative}
                    </span>
                  </TableCell>
                  <TableCell>{d.libelle}</TableCell>
                  <TableCell className="text-right font-mono text-sm">
                    {formaterMontant(d.montant, langue)}
                  </TableCell>
                  <TableCell>
                    {d.categorie ?? <span className="text-muted-foreground">—</span>}
                  </TableCell>
                  <TableCell title={d.saisisseur?.email ?? ''}>
                    {d.saisisseur?.nomComplet ?? <span className="text-muted-foreground">—</span>}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        {total > TAILLE_PAGE && (
          <nav className="mt-4 flex items-center justify-center gap-3" aria-label={t('depenses.titre')}>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => allerPage(filtres.page - 1)}
              disabled={filtres.page === 0 || chargement}
            >
              {t('depenses.pagePrecedente')}
            </Button>
            <span className="text-sm text-muted-foreground">
              {t('depenses.pageInfo', { courante: filtres.page + 1, total: pagesTotales })}
            </span>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => allerPage(filtres.page + 1)}
              disabled={filtres.page + 1 >= pagesTotales || chargement}
            >
              {t('depenses.pageSuivante')}
            </Button>
          </nav>
        )}
      </Card>
    </div>
  );
}
