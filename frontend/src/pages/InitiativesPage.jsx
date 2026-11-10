import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useAuth } from '../AuthContext';
import StatutBadge from '../components/StatutBadge';
import SanteBadge from '../components/SanteBadge';
import ProgressBar from '../components/ProgressBar';
import KanbanBoard from '../components/KanbanBoard';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import { useReferentiels } from '../i18n/referentiels';
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

const vide = {
  recherche: '',
  statut: '',
  entite: '',
  responsable: '',
  priorite: '',
  dateDu: '',
  dateAu: '',
  voirArchive: false
};

/** Lit l'ensemble des filtres depuis l'URL (vue partageable). */
const lireDepuisUrl = (params) => ({
  recherche: params.get('recherche') ?? '',
  statut: params.get('statut') ?? '',
  entite: params.get('entite') ?? '',
  responsable: params.get('responsable') ?? '',
  priorite: params.get('priorite') ?? '',
  dateDu: params.get('dateDu') ?? '',
  dateAu: params.get('dateAu') ?? '',
  voirArchive: params.get('archive') === '1' || params.get('archive') === 'true'
});

/**
 * Liste des initiatives avec recherche et filtres combinés
 * (statut, entité, responsable, période, priorité).
 */
export default function InitiativesPage() {
  const { hasPermission } = useAuth();
  const { t } = useI18n();
  const ref = useReferentiels();
  // L'URL est la source de vérité des filtres : la vue filtrée se partage et
  // survit à un rafraîchissement (y compris le lien « Archives »).
  const [searchParams, setSearchParams] = useSearchParams();
  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [initiatives, setInitiatives] = useState([]);
  const [referentiels, setReferentiels] = useState({ statuts: [], entites: [], responsables: [], priorites: [] });
  const [vue, setVue] = useState('table'); // 'table' | 'kanban'
  const [chargement, setChargement] = useState(false);
  const [erreur, setErreur] = useState('');
  const [info, setInfo] = useState('');

  useEffect(() => {
    Promise.all([
      api.get('/referentiels/statuts-initiative'),
      api.get('/referentiels/entites'),
      api.get('/referentiels/responsables'),
      api.get('/referentiels/priorites')
    ])
      .then(([s, e, r, p]) =>
        setReferentiels({ statuts: s.data, entites: e.data, responsables: r.data, priorites: p.data }))
      .catch((err) => setErreur(messageErreur(err)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Toute modification de l'URL (appliquer, réinitialiser, retour du
  // navigateur, lien partagé) recharge la liste avec les filtres correspondants.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    chargerListe(depuisUrl);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const construireParams = (f) => {
    const params = new URLSearchParams();
    if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
    if (f.statut) params.set('statut', f.statut);
    if (f.entite) params.set('entite', f.entite);
    if (f.responsable) params.set('responsable', f.responsable);
    if (f.priorite) params.set('priorite', f.priorite);
    // Période « du … au … » : initiatives actives sur l'intervalle.
    if (f.dateDu) params.set('dateDu', f.dateDu);
    if (f.dateAu) params.set('dateAu', f.dateAu);
    if (f.voirArchive) params.set('archive', 'true');
    return params;
  };

  const chargerListe = async (f = filtres) => {
    setChargement(true);
    setErreur('');
    try {
      const query = construireParams(f).toString();
      const res = await api.get(`/initiatives${query ? `?${query}` : ''}`);
      setInitiatives(res.data);
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setChargement(false);
    }
  };

  /**
   * Après un glisser-déposer sur le Kanban : la carte a changé de colonne,
   * donc de statut. On recharge la liste — et non la seule carte — car le
   * déplacement peut aussi la faire sortir des filtres actifs (glisser dans
   * « Annulée » alors que seules les initiatives « En cours » sont affichées).
   */
  const apresDeplacement = async () => {
    await chargerListe();
    setInfo({ cle: 'initiatives.deplacementEffectue' });
  };

  const appliquerFiltres = (e) => {
    e.preventDefault();
    // L'effet déclenché par le changement d'URL recharge la liste.
    setSearchParams(construireParams(filtres), { replace: true });
  };

  const reinitialiser = () => {
    setFiltres(vide);
    setSearchParams({}, { replace: true });
  };

  const archiver = async (initiative) => {
    if (!window.confirm(t('initiatives.confirmArchiver', { nom: initiative.nom }))) {
      return;
    }
    try {
      await api.patch(`/initiatives/${initiative.idInitiative}/archive`);
      setInfo({ cle: 'initiatives.initiativeArchivee', params: { nom: initiative.nom } });
      chargerListe();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  const peutArchiver = (i) =>
    hasPermission('INITIATIVE_ARCHIVER') &&
    !i.archive &&
    ['Terminée', 'Annulée'].includes(i.statut?.libelle);

  /**
   * Purge définitive d'une initiative DÉJÀ archivée (RM14) : confirmation
   * puis rechargement. Le serveur refuse toute purge hors archivage (400).
   */
  const purger = async (initiative) => {
    if (!window.confirm(t('initiatives.confirmPurger', { nom: initiative.nom }))) {
      return;
    }
    try {
      await api.delete(`/initiatives/${initiative.idInitiative}`);
      setInfo({ cle: 'initiatives.initiativePurgee', params: { nom: initiative.nom } });
      chargerListe();
    } catch (err) {
      setErreur(messageErreur(err));
    }
  };

  const champ = (champs) =>
    champs.map((c) => ({
      ...c,
      value: filtres[c.name],
      onChange: (e) => setFiltres((f) => ({ ...f, [c.name]: e.target.value }))
    }));

  const selects = [
    { name: 'statut', aria: t('initiatives.ariaStatut'), options: referentiels.statuts.map((s) => ({ value: s.idStatut, label: ref.statut(s.libelle) })), vide: t('initiatives.filtreTousStatuts') },
    { name: 'entite', aria: t('initiatives.ariaEntite'), options: referentiels.entites.map((e) => ({ value: e.idEntite, label: e.nom })), vide: t('initiatives.filtreToutesEntites') },
    { name: 'responsable', aria: t('initiatives.ariaResponsable'), options: referentiels.responsables.map((r) => ({ value: r.idUser, label: r.nomComplet })), vide: t('initiatives.filtreTousResponsables') },
    { name: 'priorite', aria: t('initiatives.ariaPriorite'), options: referentiels.priorites.map((p) => ({ value: p.idPriorite, label: ref.priorite(p.libelle) })), vide: t('initiatives.filtreToutesPriorites') }
  ].map((s) => champ([s])[0]);

  return (
    <div className="mx-auto w-full max-w-[1240px]">
      <header className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-[22px] font-semibold tracking-tight text-foreground">
            {t('initiatives.titre')}
          </h2>
          <p className="mt-0.5 text-[13.5px] text-muted-foreground">
            {t('initiatives.sousTitre')}
          </p>
        </div>
        {hasPermission('INITIATIVE_CREER') && (
          <Link to="/initiatives/nouvelle" className="text-sm font-medium text-primary hover:underline">
            {t('initiatives.nouvelle')}
          </Link>
        )}
      </header>

      {info && (
        <div className="mb-4 rounded-md border border-success/50 bg-success/50 p-3 text-sm text-success">
          {afficherMessage(t, info)}
        </div>
      )}
      {erreur && (
        <div className="mb-4 rounded-md border border-destructive/50 bg-destructive/50 p-3 text-sm text-destructive">
          {afficherMessage(t, erreur)}
        </div>
      )}

      <Card className="mb-6 p-[18px] shadow-sm">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold text-muted-foreground">
            {t('initiatives.filtres')}
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-0">
          <form className="flex flex-wrap items-start gap-3" onSubmit={appliquerFiltres}>
            <div className="flex-1 min-w-[180px] space-y-2">
              <Label htmlFor="initiativeRecherche">
                {t('initiatives.recherchePlaceholder')}
              </Label>
              <Input
                id="initiativeRecherche"
                type="search"
                placeholder={t('initiatives.recherchePlaceholder')}
                value={filtres.recherche}
                onChange={(e) => setFiltres((f) => ({ ...f, recherche: e.target.value }))}
              />
            </div>
            {selects.map((sel) => (
              <div key={sel.name} className="flex-1 min-w-[160px] space-y-2">
                <Label className="text-xs font-medium text-muted-foreground">
                  {sel.aria}
                </Label>
                <select
                  aria-label={sel.aria}
                  value={sel.value}
                  onChange={sel.onChange}
                  className="min-w-[160px] rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">{sel.vide}</option>
                  {sel.options.map((o) => (
                    <option key={o.value} value={o.value}>{o.label}</option>
                  ))}
                </select>
              </div>
            ))}
            <div className="flex-none space-y-2">
              <Label className="text-xs font-medium text-muted-foreground whitespace-nowrap">
                {t('initiatives.periodeTitre')}
              </Label>
              <div className="flex items-center gap-1">
                <input
                  type="date"
                  aria-label={t('initiatives.periodeDu')}
                  title={t('initiatives.periodeDu')}
                  value={filtres.dateDu}
                  onChange={(e) => setFiltres((f) => ({ ...f, dateDu: e.target.value }))}
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                />
                <span className="text-muted-foreground whitespace-nowrap" aria-hidden="true">
                  →
                </span>
                <input
                  type="date"
                  aria-label={t('initiatives.periodeAu')}
                  title={t('initiatives.periodeAu')}
                  value={filtres.dateAu}
                  onChange={(e) => setFiltres((f) => ({ ...f, dateAu: e.target.value }))}
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                />
              </div>
              <small className="text-xs text-muted-foreground filtre-aide">
                {t('initiatives.periodeAide')}
              </small>
            </div>
            <div className="flex-none space-y-2 flex items-end">
              <label className="flex items-center gap-2 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={filtres.voirArchive}
                  onChange={(e) => setFiltres((f) => ({ ...f, voirArchive: e.target.checked }))}
                  className="rounded border-input text-primary focus:ring-ring"
                />
                {t('initiatives.voirArchive')}
              </label>
            </div>
            <div className="flex items-end gap-2">
              <Button type="submit" disabled={chargement}>
                {t('initiatives.appliquer')}
              </Button>
              <Button type="button" variant="secondary" onClick={reinitialiser}>
                {t('initiatives.reinitialiser')}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card className="mb-6 p-[18px] shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 border border-input rounded-md p-1">
            <button
              type="button"
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${vue === 'table' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'}`}
              onClick={() => setVue('table')}
            >
              {t('initiatives.vueTableau')}
            </button>
            <button
              type="button"
              className={`px-3 py-1.5 text-sm rounded-md transition-colors ${vue === 'kanban' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'}`}
              onClick={() => setVue('kanban')}
            >
              {t('initiatives.vueKanban')}
            </button>
          </div>
          <span className="text-sm text-muted-foreground">
            {t(initiatives.length > 1 ? 'initiatives.nombrePluriel' : 'initiatives.nombreSingulier', { n: initiatives.length })}
          </span>
        </div>
        {chargement ? (
          <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">
            {t('initiatives.chargement')}
          </div>
        ) : vue === 'kanban' ? (
          <KanbanBoard
            initiatives={initiatives}
            statuts={referentiels.statuts}
            onDeplacee={apresDeplacement}
          />
        ) : initiatives.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            {t('initiatives.aucunResultat')}
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('initiatives.colCode')}</TableHead>
                <TableHead>{t('initiatives.colInitiative')}</TableHead>
                <TableHead>{t('initiatives.colEntite')}</TableHead>
                <TableHead>{t('initiatives.colResponsable')}</TableHead>
                <TableHead>{t('initiatives.colStatut')}</TableHead>
                <TableHead>{t('sante.colonne')}</TableHead>
                <TableHead>{t('initiatives.colPriorite')}</TableHead>
                <TableHead>{t('initiatives.colAvancement')}</TableHead>
                <TableHead>{t('initiatives.colDebut')}</TableHead>
                <TableHead>{t('initiatives.colFinPrevue')}</TableHead>
                <TableHead title={t('initiatives.budgetAide')}>{t('initiatives.colBudget')}</TableHead>
                <TableHead className="text-right">{t('initiatives.colActions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {initiatives.map((i) => (
                <TableRow key={i.idInitiative} className={i.enRetard ? 'ligne-retard' : ''} title={i.enRetard ? t('initiatives.enRetard') : ''}>
                  <TableCell className="text-muted-foreground">{i.code}</TableCell>
                  <TableCell>
                    <Link to={`/initiatives/${i.idInitiative}`}>{i.nom}</Link>
                  </TableCell>
                  <TableCell>{i.entite?.nom ?? '—'}</TableCell>
                  <TableCell>{i.responsable?.nomComplet ?? '—'}</TableCell>
                  <TableCell>
                    <StatutBadge libelle={i.statut?.libelle} />
                  </TableCell>
                  <TableCell>
                    <SanteBadge sante={i.sante} avecRaison />
                  </TableCell>
                  <TableCell>{ref.priorite(i.priorite?.libelle) ?? '—'}</TableCell>
                  <TableCell>
                    <ProgressBar valeur={i.avancement} />
                  </TableCell>
                  <TableCell>{i.dateDebut ?? '—'}</TableCell>
                  <TableCell>{i.dateFinPrevue ?? '—'}</TableCell>
                  <TableCell className="text-right">
                    {i.budget?.toLocaleString('fr-FR')} MAD
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center gap-1">
                      <Link to={`/initiatives/${i.idInitiative}`} className="text-xs text-primary hover:underline">
                        {t('initiatives.voir')}
                      </Link>
                      {hasPermission('INITIATIVE_MODIFIER') && !i.archive && (
                        <Link
                          to={`/initiatives/${i.idInitiative}/modifier`}
                          className="ml-1 text-xs text-primary hover:underline"
                        >
                          {t('initiatives.modifier')}
                        </Link>
                      )}
                      {peutArchiver(i) && (
                        <button
                          type="button"
                          className="ml-1 text-xs text-destructive hover:underline"
                          onClick={() => archiver(i)}
                        >
                          {t('initiatives.archiver')}
                        </button>
                      )}
                      {i.archive && hasPermission('INITIATIVE_ARCHIVER') && (
                        <button
                          type="button"
                          className="ml-1 text-xs text-destructive hover:underline"
                          onClick={() => purger(i)}
                        >
                          {t('initiatives.purger')}
                        </button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}
