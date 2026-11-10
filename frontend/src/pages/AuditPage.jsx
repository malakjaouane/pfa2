import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { messageErreur, messageErreurBlob, telechargerFichier } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Badge } from '../components/ui/badge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '../components/ui/table';
import { useToast } from '../components/ui/use-toast';

/** Taille de page fixe : un registre se feuillette, il ne s'épuise pas. */
const TAILLE_PAGE = 25;

/** Méthodes enregistrées — le serveur rejette tout autre filtre (400). */
const METHODES = ['POST', 'PUT', 'PATCH', 'DELETE'];

const vide = { email: '', methode: '', route: '', du: '', au: '' };

/** Lit les critères depuis l'URL (vue partageable, fil du navigateur). */
const lireDepuisUrl = (params) => ({
  email: params.get('email') ?? '',
  methode: METHODES.includes(params.get('methode')) ? params.get('methode') : '',
  route: params.get('route') ?? '',
  du: params.get('du') ?? '',
  au: params.get('au') ?? '',
  page: Math.max(0, parseInt(params.get('page') ?? '0', 10) || 0)
});

/** Date et heure dans la langue active (l'ISO de l'API est locale : sans « Z »). */
const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleString(langue, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });

/** Badge de statut HTTP : 2xx vert, 4xx orange, 5xx rouge. */
function badgeStatut(code) {
  if (code >= 200 && code < 300) return 'badge-vert';
  if (code >= 500) return 'badge-rouge';
  return 'badge-orange';
}

/**
 * Journal d'audit : qui a fait QUOI depuis OÙ. Lecture seule — le journal
 * est immuable (aucune API de suppression ne sait l'éditer) et l'accès est
 * réservé à UTILISATEUR_GERER, donc aux comptes qui gèrent justement les
 * utilisateurs.
 *
 * Filtres e-mail / méthode / route / période, pagination et export CSV
 * complet. L'URL porte les critères : la vue filtrée se partage.
 */
export default function AuditPage() {
  const { t, langue } = useI18n();
  const { toast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [lignes, setLignes] = useState([]);
  const [total, setTotal] = useState(0);
  const [chargement, setChargement] = useState(true);

  // Toute modification de l'URL recharge le journal.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    charger(depuisUrl);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const construireParams = (f, options = {}) => {
    const params = new URLSearchParams();
    if (f.email.trim()) params.set('email', f.email.trim());
    if (f.methode) params.set('methode', f.methode);
    if (f.route.trim()) params.set('route', f.route.trim());
    if (f.du) params.set('du', f.du);
    if (f.au) params.set('au', f.au);
    const page = options.page ?? 0;
    if (page > 0) params.set('page', String(page));
    return params;
  };

  const charger = async (f) => {
    setChargement(true);
    try {
      const params = construireParams(f, { page: f.page });
      params.set('size', String(TAILLE_PAGE));
      const res = await api.get(`/journal-audit?${params.toString()}`);
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
        `/journal-audit/export?${construireParams(filtres).toString()}`,
        'journal-audit.csv'
      );
    } catch (err) {
      toast({ title: await messageErreurBlob(err), variant: 'destructive' });
    }
  };

  const pagesTotales = Math.max(1, Math.ceil(total / TAILLE_PAGE));
  const depart = total === 0 ? 0 : filtres.page * TAILLE_PAGE + 1;
  const arrivee = Math.min((filtres.page + 1) * TAILLE_PAGE, total);
  const filtresActifs = Boolean(
    filtres.email.trim() || filtres.methode || filtres.route.trim() || filtres.du || filtres.au
  );

  const champ = (nom) => (e) => setFiltres((f) => ({ ...f, [nom]: e.target.value }));

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('audit.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('audit.sousTitre')}
          </p>
        </div>
        <Button
          type="button"
          variant="secondary"
          onClick={exporterCsv}
          disabled={lignes.length === 0}
          title={t('audit.exportAide')}
        >
          {t('audit.exportCsv')}
        </Button>
      </header>

      <Card className="mb-6 p-[18px] shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold text-muted-foreground">
            {t('audit.filtres')}
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-0">
          <form className="flex flex-wrap items-center gap-3" onSubmit={appliquerFiltres}>
            <div className="flex-1 min-w-[180px] space-y-2">
              <Label htmlFor="auditEmail">
                {t('audit.email')}
              </Label>
              <Input
                id="auditEmail"
                type="search"
                placeholder={t('audit.emailPlaceholder')}
                aria-label={t('audit.email')}
                value={filtres.email}
                onChange={champ('email')}
              />
            </div>
            <div className="flex-1 min-w-[140px] space-y-2">
              <Label className="text-xs font-medium text-muted-foreground">
                {t('audit.methode')}
              </Label>
              <select
                aria-label={t('audit.methode')}
                value={filtres.methode}
                onChange={champ('methode')}
                className="min-w-[140px] rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">{t('audit.toutesMethodes')}</option>
                {METHODES.map((m) => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </select>
            </div>
            <div className="flex-1 min-w-[200px] space-y-2">
              <Label htmlFor="auditRoute">
                {t('audit.route')}
              </Label>
              <Input
                id="auditRoute"
                type="search"
                placeholder={t('audit.routePlaceholder')}
                aria-label={t('audit.route')}
                value={filtres.route}
                onChange={champ('route')}
              />
            </div>
            <div className="flex-none space-y-2">
              <Label className="text-xs font-medium text-muted-foreground whitespace-nowrap">
                {t('audit.periodeDu')}
              </Label>
              <input
                type="date"
                aria-label={t('audit.periodeDu')}
                title={t('audit.periodeDu')}
                value={filtres.du}
                onChange={champ('du')}
                className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </div>
            <span className="text-muted-foreground whitespace-nowrap" aria-hidden="true">
              →
            </span>
            <div className="flex-none space-y-2">
              <Label className="text-xs font-medium text-muted-foreground whitespace-nowrap">
                {t('audit.periodeAu')}
              </Label>
              <input
                type="date"
                aria-label={t('audit.periodeAu')}
                title={t('audit.periodeAu')}
                value={filtres.au}
                onChange={champ('au')}
                className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </div>
            <div className="flex items-end gap-2">
              <Button type="submit" disabled={chargement}>
                {t('audit.appliquer')}
              </Button>
              <Button type="button" variant="secondary" onClick={reinitialiser}>
                {t('audit.reinitialiser')}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card className="mb-6 p-[18px] shadow-sm">
        <div className="mb-3.5 flex items-center justify-between gap-3">
          <span className="text-sm text-muted-foreground">
            {total > 0 ? t('audit.compteur', { de: depart, a: arrivee, total }) : ''}
            {filtresActifs && (
              <>
                {' · '}
                <button
                  type="button"
                  className="text-primary underline underline-offset-2 hover:text-primary/80"
                  onClick={reinitialiser}
                >
                  {t('audit.reinitialiser')}
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
            {t('audit.aucun')}
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('audit.colDate')}</TableHead>
                <TableHead>{t('audit.colEmail')}</TableHead>
                <TableHead>{t('audit.colMethode')}</TableHead>
                <TableHead>{t('audit.colRoute')}</TableHead>
                <TableHead>{t('audit.colStatut')}</TableHead>
                <TableHead>{t('audit.colIp')}</TableHead>
                <TableHead>{t('audit.colCorps')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lignes.map((l) => (
                <TableRow key={l.idAudit ?? `${l.dateAction}-${l.route}`}>
                  <TableCell>{formaterDate(l.dateAction, langue)}</TableCell>
                  <TableCell title={l.email}>
                    {l.email || <span className="text-muted-foreground">{t('audit.horsApi')}</span>}
                  </TableCell>
                  <TableCell>
                    <Badge variant="secondary">{l.methode}</Badge>
                  </TableCell>
                  <TableCell>
                    {l.idInitiative ? (
                      <Link to={`/initiatives/${l.idInitiative}`} className="text-primary hover:underline">
                        {l.route}
                      </Link>
                    ) : (
                      l.route
                    )}
                  </TableCell>
                  <TableCell>
                    <Badge variant={badgeStatut(l.statutHttp) === 'badge-vert' ? 'success' : badgeStatut(l.statutHttp) === 'badge-rouge' ? 'destructive' : 'secondary'}>
                      {l.statutHttp}
                    </Badge>
                  </TableCell>
                  <TableCell>{l.ipAdresse ?? <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell>
                    <code className="text-xs text-muted-foreground">{l.corps || '—'}</code>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        {total > TAILLE_PAGE && (
          <nav className="mt-4 flex items-center justify-center gap-3" aria-label={t('audit.titre')}>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => allerPage(filtres.page - 1)}
              disabled={filtres.page === 0 || chargement}
            >
              {t('audit.pagePrecedente')}
            </Button>
            <span className="text-sm text-muted-foreground">
              {t('audit.pageInfo', { courante: filtres.page + 1, total: pagesTotales })}
            </span>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => allerPage(filtres.page + 1)}
              disabled={filtres.page + 1 >= pagesTotales || chargement}
            >
              {t('audit.pageSuivante')}
            </Button>
          </nav>
        )}
      </Card>
    </div>
  );
}
