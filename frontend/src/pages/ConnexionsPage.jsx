import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Badge } from '../components/ui/badge';
import { EmptyState } from '../components/ui/empty-state';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '../components/ui/table';
import { IconSearch } from '../components/Icons';
import { useToast } from '../components/ui/use-toast';

/** Taille de page fixe : un tableau de lecture se feuillette, il ne s'épuise pas. */
const TAILLE_PAGE = 25;

const vide = { recherche: '', succes: '', du: '', au: '' };

/** Lit l'ensemble des critères depuis l'URL (vue partageable, fil du navigateur). */
const lireDepuisUrl = (params) => {
  const page = parseInt(params.get('page') ?? '0', 10);
  return {
    recherche: params.get('recherche') ?? '',
    succes: params.get('succes') ?? '',
    du: params.get('du') ?? '',
    au: params.get('au') ?? '',
    page: Number.isNaN(page) || page < 0 ? 0 : page
  };
};

/** Date et heure dans la langue active (l'ISO de l'API est locale : sans « Z »). */
const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleString(langue, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });

/** Champs de filtre alignés sur une ligne (recherche, sélecteur, dates). */
const CHAMP_FILTRE =
  'min-w-[130px] flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring';

/**
 * Page « Historique des connexions » : journal des tentatives de
 * connexion (réussies ou échouées), traçabilité loi 09-08.
 *
 * Accès réservé aux administrateurs (UTILISATEUR_GERER, imposé par
 * SecurityConfig sur TOUTES les routes /api/historique-connexions) : le
 * journal expose e-mails et adresses IP. Recherche (e-mail, IP, nom),
 * filtre de résultat et période, pagination — puis suppression d'une
 * ligne ou purge totale avec confirmation. Tri fixe : du plus récent au
 * plus ancien. L'URL porte les critères : la vue filtrée se partage.
 */
export default function ConnexionsPage() {
  const { t, langue } = useI18n();
  const { toast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [lignes, setLignes] = useState([]);
  const [total, setTotal] = useState(0);
  const [chargement, setChargement] = useState(true);

  const charger = async (f) => {
    setChargement(true);
    try {
      const params = new URLSearchParams();
      if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
      if (f.succes) params.set('succes', f.succes);
      if (f.du) params.set('du', f.du);
      if (f.au) params.set('au', f.au);
      params.set('page', String(f.page));
      params.set('size', String(TAILLE_PAGE));
      const res = await api.get(`/historique-connexions?${params.toString()}`);
      setLignes(res.data);
      const compte = parseInt(res.headers['x-total-count'] ?? '', 10);
      setTotal(Number.isNaN(compte) ? res.data.length : compte);
    } catch (err) {
      toast({ title: messageErreur(err), variant: 'destructive' });
    } finally {
      setChargement(false);
    }
  };

  // Toute modification de l'URL (appliquer, pagination, retour du
  // navigateur) recharge la liste avec les critères correspondants.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    charger(depuisUrl);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const construireParams = (f, options = {}) => {
    const params = new URLSearchParams();
    if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
    if (f.succes) params.set('succes', f.succes);
    if (f.du) params.set('du', f.du);
    if (f.au) params.set('au', f.au);
    const page = options.page ?? 0;
    if (page > 0) params.set('page', String(page));
    return params;
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

  /**
   * Suppression d'une ligne (admin) : confirmation puis rechargement.
   * Si la ligne supprimée était la dernière de sa page, on revient à la
   * page précédente pour ne pas afficher une page vide sur un total > 0.
   */
  const demanderSuppression = (ev) => {
    const libelle = `${ev.email} — ${formaterDate(ev.dateConnexion, langue)}`;
    if (!window.confirm(t('connexions.supprimerConfirmer', { ligne: libelle }))) return;
    api.delete(`/historique-connexions/${ev.idConnexion}`)
      .then(() => {
        toast({ title: t('connexions.supprimeOk'), variant: 'success' });
        if (lignes.length === 1 && filtres.page > 0) {
          allerPage(filtres.page - 1);
        } else {
          charger(filtres);
        }
      })
      .catch((err) => {
        toast({ title: messageErreur(err), variant: 'destructive' });
      });
  };

  /**
   * Purge TOUT le journal (admin) : confirmation puis rechargement — on
   * revient à la première page si on était ailleurs.
   */
  const purgerTout = () => {
    if (!window.confirm(t('connexions.purgerConfirmer'))) return;
    api.delete('/historique-connexions')
      .then(() => {
        toast({ title: t('connexions.purgeOk'), variant: 'success' });
        if (filtres.page > 0) {
          allerPage(0);
        } else {
          charger(filtres);
        }
      })
      .catch((err) => {
        toast({ title: messageErreur(err), variant: 'destructive' });
      });
  };

  const pagesTotales = Math.max(1, Math.ceil(total / TAILLE_PAGE));
  const depart = total === 0 ? 0 : filtres.page * TAILLE_PAGE + 1;
  const arrivee = Math.min((filtres.page + 1) * TAILLE_PAGE, total);
  const filtresActifs = Boolean(
    filtres.recherche.trim() || filtres.succes || filtres.du || filtres.au
  );

  const champRecherche = (e) => setFiltres((f) => ({ ...f, recherche: e.target.value }));
  const champ = (nom) => (e) => setFiltres((f) => ({ ...f, [nom]: e.target.value }));

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('connexions.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('connexions.sousTitre')}
          </p>
        </div>
        {total > 0 && (
          <Button type="button" variant="destructive" onClick={purgerTout}>
            {t('connexions.purger')}
          </Button>
        )}
      </header>

      <form
        className="mb-5 flex flex-col gap-3 rounded-xl border border-input bg-card p-[18px] shadow"
        onSubmit={appliquerFiltres}
      >
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="search"
            className={CHAMP_FILTRE}
            placeholder={t('connexions.recherchePlaceholder')}
            aria-label={t('connexions.recherchePlaceholder')}
            value={filtres.recherche}
            onChange={champRecherche}
          />
          <select
            className={CHAMP_FILTRE}
            aria-label={t('connexions.ariaResultat')}
            value={filtres.succes}
            onChange={champ('succes')}
          >
            <option value="">{t('connexions.tousResultats')}</option>
            <option value="true">{t('connexions.succesOption')}</option>
            <option value="false">{t('connexions.echecOption')}</option>
          </select>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="whitespace-nowrap text-[13px] font-semibold">
            {t('connexions.periodeTitre')}
          </span>
          <input
            type="date"
            className={CHAMP_FILTRE}
            aria-label={t('connexions.periodeDu')}
            title={t('connexions.periodeDu')}
            value={filtres.du}
            onChange={champ('du')}
          />
          <span className="text-muted-foreground" aria-hidden="true">→</span>
          <input
            type="date"
            className={CHAMP_FILTRE}
            aria-label={t('connexions.periodeAu')}
            title={t('connexions.periodeAu')}
            value={filtres.au}
            onChange={champ('au')}
          />
          <small className="text-xs text-muted-foreground">
            {t('connexions.periodeAide')}
          </small>
          <Button type="submit" disabled={chargement}>{t('connexions.appliquer')}</Button>
          <Button type="button" variant="secondary" onClick={reinitialiser}>
            {t('connexions.reinitialiser')}
          </Button>
        </div>
      </form>

      <Card className="mb-5 p-[18px]">
        <div className="mb-3.5 flex items-center justify-between gap-3">
          <span className="text-sm text-muted-foreground">
            {t('connexions.compteur', { de: depart, a: arrivee, total })}
            {filtresActifs && (
              <>
                {' · '}
                <button
                  type="button"
                  className="text-primary underline underline-offset-2 hover:text-primary/80"
                  onClick={reinitialiser}
                >
                  {t('connexions.reinitialiser')}
                </button>
              </>
            )}
          </span>
        </div>

        {chargement ? (
          <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">
            {t('connexions.chargement')}
          </div>
        ) : lignes.length === 0 ? (
          <EmptyState
            icon={<IconSearch size={28} />}
            titre={t('connexions.aucunResultat')}
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('connexions.colDate')}</TableHead>
                <TableHead>{t('connexions.colUtilisateur')}</TableHead>
                <TableHead>{t('connexions.colEmail')}</TableHead>
                <TableHead>{t('connexions.colIp')}</TableHead>
                <TableHead>{t('connexions.colResultat')}</TableHead>
                <TableHead>{t('connexions.colMotif')}</TableHead>
                <TableHead>{t('connexions.colActions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lignes.map((ev) => (
                <TableRow key={ev.idConnexion}>
                  <TableCell>{formaterDate(ev.dateConnexion, langue)}</TableCell>
                  <TableCell title={ev.utilisateur?.email ?? ''}>
                    {ev.utilisateur?.nomComplet ?? (
                      <span className="text-muted-foreground">
                        {t('connexions.compteSupprime')}
                      </span>
                    )}
                  </TableCell>
                  <TableCell>{ev.email}</TableCell>
                  <TableCell>{ev.adresseIp}</TableCell>
                  <TableCell>
                    <Badge
                      className={
                        ev.succes
                          ? 'border-transparent bg-success/15 text-success hover:bg-success/15'
                          : 'border-transparent bg-destructive/15 text-destructive hover:bg-destructive/15'
                      }
                    >
                      {ev.succes
                        ? t('connexions.resultatSucces')
                        : t('connexions.resultatEchec')}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {ev.motif ?? <span className="text-muted-foreground">—</span>}
                  </TableCell>
                  <TableCell>
                    <Button
                      type="button"
                      variant="destructive"
                      size="sm"
                      onClick={() => demanderSuppression(ev)}
                    >
                      {t('connexions.supprimer')}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        {total > TAILLE_PAGE && (
          <nav
            className="flex items-center justify-center gap-3 pt-3.5"
            aria-label={t('connexions.titre')}
          >
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => allerPage(filtres.page - 1)}
              disabled={filtres.page === 0 || chargement}
            >
              {t('connexions.pagePrecedente')}
            </Button>
            <span className="text-sm text-muted-foreground">
              {t('connexions.pageInfo', { courante: filtres.page + 1, total: pagesTotales })}
            </span>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => allerPage(filtres.page + 1)}
              disabled={filtres.page + 1 >= pagesTotales || chargement}
            >
              {t('connexions.pageSuivante')}
            </Button>
          </nav>
        )}
      </Card>
    </div>
  );
}
