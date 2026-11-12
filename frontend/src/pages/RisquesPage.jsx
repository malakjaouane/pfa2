import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { messageErreur, messageErreurBlob, telechargerFichier } from '../api';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import SanteBadge from '../components/SanteBadge';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { useToast } from '../components/ui/use-toast';
import { Badge } from '../components/ui/badge';
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

/** Statuts d'un risque — doit suivre Risque.STATUT_* côté backend. */
const STATUTS = ['ouverte', 'attenuation', 'close'];

const vide = { recherche: '', statut: '', initiative: '' };

/** Lit les critères depuis l'URL (vue partageable, fil du navigateur). */
const lireDepuisUrl = (params) => ({
  recherche: params.get('recherche') ?? '',
  statut: STATUTS.includes(params.get('statut')) ? params.get('statut') : '',
  initiative: params.get('initiative') ?? '',
  page: Math.max(0, parseInt(params.get('page') ?? '0', 10) || 0)
});

const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleDateString(langue, { day: '2-digit', month: 'short', year: 'numeric' });

/**
 * Registre global des risques : toutes les initiatives, lecture ouverte
 * (INITIATIVE_CONSULTER), suppression réservée à RISQUE_GERER (19).
 *
 * Filtres recherche / statut / initiative, tri du plus critique au moins
 * critique (côté serveur), pagination et export CSV complet. L'URL porte
 * les critères : la vue filtrée se partage, c'est ce que fait le lien
 * « Risques » depuis une fiche initiative.
 */
export default function RisquesPage() {
  const { t, langue } = useI18n();
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [lignes, setLignes] = useState([]);
  const [total, setTotal] = useState(0);
  const [initiatives, setInitiatives] = useState([]);
  const [chargement, setChargement] = useState(true);
  const { toast } = useToast();

  const peutGerer = Boolean(user?.permissions?.includes('RISQUE_GERER'));

  // Liste déroulante des initiatives (filtre) : chargée une fois.
  useEffect(() => {
    api.get('/initiatives?size=200')
      .then((res) => setInitiatives(res.data ?? []))
      .catch((err) => toast({ title: messageErreur(err), variant: 'destructive' }));
  }, []);

  // Toute modification de l'URL (appliquer, suppression, pagination, lien
  // partagé, retour du navigateur) recharge le registre.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    charger(depuisUrl);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const construireParams = (f, options = {}) => {
    const params = new URLSearchParams();
    if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
    if (f.statut) params.set('statut', f.statut);
    if (f.initiative) params.set('initiative', f.initiative);
    const page = options.page ?? 0;
    if (page > 0) params.set('page', String(page));
    return params;
  };

  const charger = async (f) => {
    setChargement(true);
    try {
      const params = construireParams(f, { page: f.page });
      params.set('size', String(TAILLE_PAGE));
      const res = await api.get(`/risques?${params.toString()}`);
      setLignes(res.data ?? []);
      const compte = parseInt(res.headers['x-total-count'] ?? '', 10);
      setTotal(Number.isNaN(compte) ? (res.data?.length ?? 0) : compte);
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
      setLignes([]);
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
    try {
      await telechargerFichier(
        `/risques/export?${construireParams(filtres).toString()}`,
        'risques.csv'
      );
    } catch (err) {
      toast({ title: await messageErreurBlob(err), variant: 'destructive' });
    }
  };

  const supprimer = async (risque) => {
    if (!window.confirm(t('risques.confirmSupprimer', { titre: risque.titre }))) return;
    try {
      await api.delete(`/risques/${risque.idRisque}`);
      charger(filtres);
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
    }
  };

  const pagesTotales = Math.max(1, Math.ceil(total / TAILLE_PAGE));
  const depart = total === 0 ? 0 : filtres.page * TAILLE_PAGE + 1;
  const arrivee = Math.min((filtres.page + 1) * TAILLE_PAGE, total);
  const filtresActifs = Boolean(filtres.recherche.trim() || filtres.statut || filtres.initiative);

  const champ = (nom) => (e) => setFiltres((f) => ({ ...f, [nom]: e.target.value }));

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('risques.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('risques.sousTitre')}
          </p>
        </div>
        <Button
          type="button"
          variant="secondary"
          onClick={exporterCsv}
          disabled={lignes.length === 0}
          title={t('risques.exportAide')}
        >
          {t('risques.exportCsv')}
        </Button>
      </header>

      <Card className="mb-6 p-[18px] shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold text-muted-foreground">
            {t('risques.filtres')}
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-0">
          <form className="flex flex-wrap items-center gap-3" onSubmit={appliquerFiltres}>
            <div className="flex-1 min-w-[180px] space-y-2">
              <Label htmlFor="risqueRecherche">
                {t('risques.recherche')}
              </Label>
              <Input
                id="risqueRecherche"
                type="search"
                placeholder={t('risques.recherche')}
                aria-label={t('risques.recherche')}
                value={filtres.recherche}
                onChange={champ('recherche')}
              />
            </div>
            <div className="flex-1 min-w-[160px] space-y-2">
              <Label className="text-xs font-medium text-muted-foreground">
                {t('risques.colStatut')}
              </Label>
              <select
                aria-label={t('risques.colStatut')}
                value={filtres.statut}
                onChange={champ('statut')}
                className="min-w-[160px] rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">{t('risques.tousStatuts')}</option>
                {STATUTS.map((s) => (
                  <option key={s} value={s}>{t(`suivi.risques.statut.${s}`)}</option>
                ))}
              </select>
            </div>
            <div className="flex-1 min-w-[180px] space-y-2">
              <Label className="text-xs font-medium text-muted-foreground">
                {t('risques.colInitiative')}
              </Label>
              <select
                aria-label={t('risques.colInitiative')}
                value={filtres.initiative}
                onChange={champ('initiative')}
                className="min-w-[180px] rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">{t('risques.toutesInitiatives')}</option>
                {initiatives.map((i) => (
                  <option key={i.idInitiative} value={i.idInitiative}>{i.code}</option>
                ))}
              </select>
            </div>
            <div className="flex items-end gap-2">
              <Button type="submit" disabled={chargement}>
                {t('risques.appliquer')}
              </Button>
              <Button type="button" variant="secondary" onClick={reinitialiser}>
                {t('risques.reinitialiser')}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card className="mb-6 p-[18px] shadow-sm">
        <div className="mb-3.5 flex items-center justify-between gap-3">
          <span className="text-sm text-muted-foreground">
            {total > 0 ? t('risques.compteur', { de: depart, a: arrivee, total }) : ''}
            {filtresActifs && (
              <>
                {' · '}
                <button
                  type="button"
                  className="text-primary underline underline-offset-2 hover:text-primary/80"
                  onClick={reinitialiser}
                >
                  {t('risques.reinitialiser')}
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
            {t('risques.aucun')}
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('risques.colTitre')}</TableHead>
                <TableHead>{t('risques.colInitiative')}</TableHead>
                <TableHead>{t('risques.colScore')}</TableHead>
                <TableHead>{t('risques.colStatut')}</TableHead>
                <TableHead>{t('risques.colEcheance')}</TableHead>
                <TableHead>{t('risques.colResponsable')}</TableHead>
                {peutGerer && <TableHead className="text-right" aria-label={t('risques.supprimer')} />}
              </TableRow>
            </TableHeader>
            <TableBody>
              {lignes.map((r) => (
                <TableRow key={r.idRisque}>
                  <TableCell>
                    <strong>{r.titre}</strong>
                    {r.description && (
                      <div className="mt-1 text-sm text-muted-foreground">{r.description}</div>
                    )}
                  </TableCell>
                  <TableCell>
                    {r.idInitiative ? (
                      <Link to={`/initiatives/${r.idInitiative}`} className="text-primary hover:underline">
                        {r.codeInitiative}
                      </Link>
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </TableCell>
                  <TableCell>
                    <SanteBadge sante={{ niveau: r.niveau }} />
                    <span className="ml-1 text-xs text-muted-foreground">
                      {r.score}/25
                    </span>
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant="secondary"
                      className={`border-transparent ${
                        r.statut === 'close'
                          ? 'bg-muted/50 text-muted-foreground'
                          : r.statut === 'attenuation'
                            ? 'bg-amber/15 text-amber-800'
                            : 'bg-blue/15 text-blue-800'
                      }`}
                    >
                      {t(`suivi.risques.statut.${r.statut}`)}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {r.echeance
                      ? formaterDate(r.echeance, langue)
                      : <span className="text-muted-foreground">—</span>}
                  </TableCell>
                  <TableCell title={r.responsable?.email ?? ''}>
                    {r.responsable?.nomComplet ?? <span className="text-muted-foreground">—</span>}
                  </TableCell>
                  {peutGerer && (
                    <TableCell className="text-right">
                      <Button
                        type="button"
                        variant="destructive"
                        size="sm"
                        onClick={() => supprimer(r)}
                        aria-label={`${t('risques.supprimer')} : ${r.titre}`}
                      >
                        ×
                      </Button>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        {total > TAILLE_PAGE && (
          <nav className="mt-4 flex items-center justify-center gap-3" aria-label={t('risques.titre')}>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => allerPage(filtres.page - 1)}
              disabled={filtres.page === 0 || chargement}
            >
              {t('risques.pagePrecedente')}
            </Button>
            <span className="text-sm text-muted-foreground">
              {t('risques.pageInfo', { courante: filtres.page + 1, total: pagesTotales })}
            </span>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => allerPage(filtres.page + 1)}
              disabled={filtres.page + 1 >= pagesTotales || chargement}
            >
              {t('risques.pageSuivante')}
            </Button>
          </nav>
        )}
      </Card>
    </div>
  );
}
