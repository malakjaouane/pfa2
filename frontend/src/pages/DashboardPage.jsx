import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Chart as ChartJS,
  ArcElement,
  CategoryScale,
  LinearScale,
  BarElement,
  Tooltip,
  Legend
} from 'chart.js';
import { Doughnut, Bar } from 'react-chartjs-2';
import api, { messageErreur, messageErreurBlob, telechargerFichier } from '../api';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { useReferentiels } from '../i18n/referentiels';
import { badgeTypeJournal, libelleType, texteEvenement } from '../i18n/journal';
import { Badge } from '../components/ui/badge';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '../components/ui/card';
import { Label } from '../components/ui/label';
import { Button } from '../components/ui/button';
import { useToast } from '../components/ui/use-toast';
import {
  IconProject,
  IconLayers,
  IconCheck,
  IconAlert,
  IconWallet,
  IconFlag,
  IconPlus,
  IconArrowRight,
  IconSearch,
  IconDownload
} from '../components/Icons';

ChartJS.register(ArcElement, CategoryScale, LinearScale, BarElement, Tooltip, Legend);

const COULEUR_STATUT = {
  'À planifier': '#8590A2',
  'En cours': '#0C66E4',
  'En pause': '#E56910',
  'Terminée': '#22A06B',
  'Archivée': '#C9372C'
};

const COULEUR_PRIORITE = {
  Basse: '#579DFF',
  Moyenne: '#0C66E4',
  Haute: '#E56910',
  Critique: '#C9372C'
};

function PrioriteBadge({ libelle }) {
  const ref = useReferentiels();
  if (!libelle) return <span className="text-xs text-muted-foreground">—</span>;
  return <Badge variant="outline" className="text-xs">{ref.priorite(libelle)}</Badge>;
}

const formatBudget = (valeur, t) => {
  const n = valeur ?? 0;
  if (Math.abs(n) >= 1000000) {
    return t('dashboard.budget.millions', {
      valeur: (n / 1000000).toLocaleString('fr-FR', { maximumFractionDigits: 2 })
    });
  }
  if (Math.abs(n) >= 1000) {
    return t('dashboard.budget.milliers', {
      valeur: (n / 1000).toLocaleString('fr-FR', { maximumFractionDigits: 0 })
    });
  }
  return t('dashboard.budget.dirhams', { valeur: n.toLocaleString('fr-FR') });
};

function formatDate(iso) {
  if (!iso) return '—';
  const [a, m, j] = iso.split('-').map(Number);
  return new Date(a, m - 1, j).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
}

function joursDeRetard(iso) {
  if (!iso) return 0;
  const [a, m, j] = iso.split('-').map(Number);
  const fin = new Date(a, m - 1, j);
  const aujourdHui = new Date();
  aujourdHui.setHours(0, 0, 0, 0);
  return Math.max(0, Math.round((aujourdHui - fin) / 86400000));
}

const filtresVides = {
  statut: '',
  entite: '',
  responsable: '',
  dateDu: '',
  dateAu: ''
};

const lireDepuisUrl = (params) => ({
  statut: params.get('statut') ?? '',
  entite: params.get('entite') ?? '',
  responsable: params.get('responsable') ?? '',
  dateDu: params.get('dateDu') ?? '',
  dateAu: params.get('dateAu') ?? ''
});

const construireParams = (f) => {
  const params = new URLSearchParams();
  if (f.statut) params.set('statut', f.statut);
  if (f.entite) params.set('entite', f.entite);
  if (f.responsable) params.set('responsable', f.responsable);
  if (f.dateDu) params.set('dateDu', f.dateDu);
  if (f.dateAu) params.set('dateAu', f.dateAu);
  return params;
};

function StatutBarre({ items, total, t }) {
  const ref = useReferentiels();
  const affiches = items.filter((d) => d.count > 0);
  return (
    <div className="bg-card/50 rounded-lg p-4 border border-border">
      <h4 className="text-xs font-semibold text-muted-foreground mb-2">{t('dashboard.etat.parStatut')}</h4>
      <div className="flex h-40 items-end justify-between gap-2">
        {affiches.map((d) => {
          const pct = total ? Math.round((d.count / total) * 100) : 0;
          const h = Math.max(20, (pct / 100) * 160);
          const couleur = COULEUR_STATUT[d.libelle] ?? '#8590A2';
          return (
            <div key={d.libelle} className="flex flex-col items-center justify-end gap-1">
              <div className={`w-full rounded-t-md transition-all duration-500`} style={{ height: `${h}px`, backgroundColor: couleur }} />
              <span className="text-xs">{d.count}</span>
              <span className="text-xs text-muted-foreground">{ref.statut(d.libelle)}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function PrioriteBarre({ items, total, t }) {
  const ref = useReferentiels();
  const affiches = items.filter((d) => d.count > 0);
  return (
    <div className="bg-card/50 rounded-lg p-4 border border-border">
      <h4 className="text-xs font-semibold text-muted-foreground mb-2">{t('dashboard.etat.parPriorite')}</h4>
      <div className="flex h-40 items-end justify-between gap-2">
        {affiches.map((d) => {
          const pct = total ? Math.round((d.count / total) * 100) : 0;
          const h = Math.max(20, (pct / 100) * 160);
          const couleur = COULEUR_PRIORITE[d.libelle] ?? '#0C66E4';
          return (
            <div key={d.libelle} className="flex flex-col items-center justify-end gap-1">
              <div className={`w-full rounded-t-md transition-all duration-500`} style={{ height: `${h}px`, backgroundColor: couleur }} />
              <span className="text-xs">{d.count}</span>
              <span className="text-xs text-muted-foreground">{ref.priorite(d.libelle)}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function AvancementMoyen({ kpis, t }) {
  return (
    <div className="bg-card/50 rounded-lg p-4 border border-border">
      <h4 className="text-xs font-semibold text-muted-foreground mb-2">{t('dashboard.etat.avancementMoyen')}</h4>
      <div className="flex flex-col items-center justify-center h-full">
        <span className="text-3xl font-bold">{kpis.avancementMoyen}<span className="text-lg">%</span></span>
        <div className="w-full h-2 bg-border rounded-full mt-4">
          <div className="h-full bg-primary rounded-full" style={{ width: `${kpis.avancementMoyen}%` }} />
        </div>
        <div className="flex justify-between text-xs text-muted-foreground mt-1">
          <span>{t('dashboard.kpi.enCours')}</span>
          <span>{t('dashboard.kpi.enPause')}</span>
          <span>{t('dashboard.kpi.terminees')}</span>
        </div>
        <div className="flex justify-between text-xs mt-1">
          <span>{kpis.enCours}</span>
          <span>{kpis.enPause}</span>
          <span>{kpis.terminees}</span>
        </div>
      </div>
    </div>
  );
}

function RecentesSection({ recentes, initiatives, rechercheRecente, setRechercheRecente, t, navigate }) {
  const ref = useReferentiels();
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">{t('dashboard.recentes.titre')}</CardTitle>
        <CardDescription className="text-xs text-muted-foreground">{t('dashboard.recentes.sousTitre')}</CardDescription>
      </CardHeader>
      <CardContent className="pt-0">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <IconSearch size={16} className="text-muted-foreground" />
            <input
              type="text"
              value={rechercheRecente}
              onChange={(e) => setRechercheRecente(e.target.value)}
              className="flex-1 rounded-md border border-input bg-background px-3 py-1.5 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label={t('dashboard.recentes.rechercheAria')}
            />
          </div>
          <Link to="/initiatives" className="btn btn-outline btn-sm">{t('dashboard.recentes.voirToutes')}</Link>
        </div>
        <div className="space-y-2">
          {recentes.length > 0 ? (
            <div className="grid gap-2">
              {recentes.map((i) => (
                <div key={i.idInitiative} className="flex items-center justify-between p-3 rounded-lg border border-border cursor-pointer group" onClick={() => navigate(`/initiatives/${i.idInitiative}`)}>
                  <div className="flex-1 min-w-0">
                    <Link to={`/initiatives/${i.idInitiative}`} className="font-medium text-foreground hover:text-primary" onClick={(e) => e.stopPropagation()}>
                      {i.nom}
                    </Link>
                    <div className="text-xs text-muted-foreground">{i.entite?.nom ?? '—'}</div>
                  </div>
                  <div className="flex items-center gap-2 text-xs">
                    <Badge variant="outline" className={COULEUR_STATUT[i.statut?.libelle] ? `text-[color:${COULEUR_STATUT[i.statut.libelle] + ']'}` : ''}>
                      {ref.statut(i.statut?.libelle)}
                    </Badge>
                    <PrioriteBadge libelle={i.priorite?.libelle} />
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-xs text-muted-foreground text-center py-4">
              {initiatives.length === 0 ? t('dashboard.table.aucune') : t('dashboard.table.aucuneRecherche')}
            </p>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function SurveillerSection({ enRetard, joursDeRetard, t, navigate }) {
  const ref = useReferentiels();
  const hasRetard = enRetard.length > 0;
  return (
    <section id="surveillance" className="mb-6">
      <div className="flex items-center justify-between mb-3">
        <div>
          <h3 className="text-sm font-semibold">{t('dashboard.surveiller.titre')}</h3>
          <p className="text-xs text-muted-foreground">{t('dashboard.surveiller.sousTitre')}</p>
        </div>
        {hasRetard && <Badge variant="destructive" className="text-xs">{enRetard.length}</Badge>}
      </div>
      {hasRetard ? (
        <div className="space-y-2">
          {enRetard.map((i) => (
            <div key={i.idInitiative} className="flex items-center justify-between p-3 rounded-lg border border-border bg-card/50 cursor-pointer group hover:bg-accent/50" onClick={() => navigate(`/initiatives/${i.idInitiative}`)}>
              <div className="flex items-center gap-3">
                <IconAlert size={18} className="text-red-500" />
                <div className="flex-1 min-w-0">
                  <Link to={`/initiatives/${i.idInitiative}`} className="font-medium text-foreground hover:text-primary" onClick={(e) => e.stopPropagation()}>
                    {i.nom}
                  </Link>
                  <div className="text-xs text-muted-foreground">{i.code}</div>
                </div>
                <div className="flex items-center gap-2 text-xs">
                  <span className="text-red-500 font-medium">{joursDeRetard(i.dateFinPrevue)}j</span>
                  <Badge variant="outline" className={COULEUR_STATUT[i.statut?.libelle] ? `text-[color:${COULEUR_STATUT[i.statut.libelle] + ']'}` : ''} size="sm">
                    {ref.statut(i.statut?.libelle)}
                  </Badge>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <Card className="text-center py-8">
          <IconCheck size={24} className="mx-auto mb-2 text-green-500" />
          <p className="text-sm font-medium">{t('dashboard.surveiller.aucun')}</p>
        </Card>
      )}
    </section>
  );
}

function RisquesSection({ risquesCritiques, peutConsulterInitiatives, t, navigate }) {
  if (!peutConsulterInitiatives) return null;
  const hasRisques = risquesCritiques.length > 0;
  return (
    <section className="mb-6">
      <div className="flex items-center justify-between mb-3">
        <div>
          <h3 className="text-sm font-semibold">{t('dashboard.risques.titre')}</h3>
          <p className="text-xs text-muted-foreground">{t('dashboard.risques.sousTitre')}</p>
        </div>
        {hasRisques && <Badge variant="destructive" className="text-xs">{risquesCritiques.length}</Badge>}
      </div>
      {hasRisques ? (
        <div className="space-y-2">
          {risquesCritiques.map((r) => (
            <div key={r.idRisque} className="flex items-center justify-between p-3 rounded-lg border border-border bg-card/50 cursor-pointer group hover:bg-accent/50" onClick={() => navigate(`/initiatives/${r.idInitiative}`)}>
              <div className="flex items-center gap-3">
                <IconAlert size={18} className="text-red-500" />
                <div className="flex-1 min-w-0">
                  <Link to={`/initiatives/${r.idInitiative}`} className="font-medium text-foreground hover:text-primary" onClick={(e) => e.stopPropagation()}>
                    {r.nomInitiative}
                  </Link>
                  <div className="text-xs text-muted-foreground">{r.codeInitiative}</div>
                </div>
                <div className="flex items-center gap-2 text-xs">
                  <Badge variant="destructive" className="text-[10px]">{t('dashboard.risques.score', { score: r.score })}</Badge>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <Card className="text-center py-8">
          <IconCheck size={24} className="mx-auto mb-2 text-green-500" />
          <p className="text-sm font-medium">{t('dashboard.risques.aucun')}</p>
        </Card>
      )}
    </section>
  );
}

function ActiviteSection({ activite, peutConsulterInitiatives, t, langue, navigate }) {
  if (!peutConsulterInitiatives) return null;
  const hasActivite = activite.length > 0;
  return (
    <section className="mb-6">
      <div className="flex items-center justify-between mb-3">
        <div>
          <h3 className="text-sm font-semibold">{t('dashboard.activite.titre')}</h3>
          <p className="text-xs text-muted-foreground">{t('dashboard.activite.sousTitre')}</p>
        </div>
        {hasActivite && <Badge variant="outline" className="text-xs">{activite.length}</Badge>}
      </div>
      {hasActivite ? (
        <Card>
          <CardContent className="pt-0">
            <ol className="space-y-2">
              {activite.map((ev) => (
                <li key={ev.id} className="flex items-start gap-3 p-3 rounded-lg border border-border hover:bg-accent/50">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 text-xs">
                      <span className={`badge ${badgeTypeJournal(ev.typeObjet)} text-[10px]`}>
                        {libelleType(t, ev.typeObjet)}
                      </span>
                      <span className="text-muted-foreground">{texteEvenement(t, ev)}</span>
                      {ev.initiative && (
                        <Link to={`/initiatives/${ev.initiative.idInitiative}`} className="text-xs font-medium text-primary hover:underline">
                          {' · '}{ev.initiative.code}
                        </Link>
                      )}
                    </div>
                    <div className="text-xs text-muted-foreground mt-1">
                      {new Date(ev.dateChangement).toLocaleString(langue, {
                        day: '2-digit', month: 'short', year: 'numeric',
                        hour: '2-digit', minute: '2-digit'
                      })}
                      {' | '}{ev.utilisateur?.nomComplet ?? t('initiativeDetail.auteurInconnu')}
                    </div>
                  </div>
                </li>
              ))}
            </ol>
            <Link to="/historique" className="flex items-center gap-2 text-xs font-medium text-primary hover:text-primary/80">
              {t('dashboard.activite.voirTout')} <IconArrowRight size={14} />
            </Link>
          </CardContent>
        </Card>
      ) : (
        <Card className="text-center py-8">
          <IconCheck size={24} className="mx-auto mb-2 text-green-500" />
          <p className="text-sm font-medium">{t('dashboard.activite.aucune')}</p>
        </Card>
      )}
    </section>
  );
}

export default function DashboardPage() {
  const { user, hasPermission } = useAuth();
  const { t, langue } = useI18n();
  const ref = useReferentiels();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [kpis, setKpis] = useState(null);
  const [initiatives, setInitiatives] = useState([]);
  const [referentiels, setReferentiels] = useState({ statuts: [], entites: [], responsables: [] });
  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [chargement, setChargement] = useState(true);
  const { toast } = useToast();
  const [rechercheRecente, setRechercheRecente] = useState('');

  const peutConsulterInitiatives = hasPermission('INITIATIVE_CONSULTER');
  const filtresActifs = Object.values(filtres).some((v) => v !== '');

  useEffect(() => {
    Promise.all([
      api.get('/referentiels/statuts-initiative'),
      api.get('/referentiels/entites'),
      api.get('/referentiels/responsables')
    ])
      .then(([s, e, r]) => setReferentiels({ statuts: s.data, entites: e.data, responsables: r.data }))
      .catch(() => {});
  }, []);

  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    const query = construireParams(depuisUrl).toString();
    const suffixe = query ? `?${query}` : '';

    let actif = true;
    setChargement(true);
    const requetes = [api.get(`/dashboard${suffixe}`)];
    if (peutConsulterInitiatives) requetes.push(api.get(`/initiatives${suffixe}`));

    Promise.all(requetes)
      .then(([d, i]) => {
        if (!actif) return;
        setKpis(d.data);
        if (i) setInitiatives(i.data);
      })
      .catch((err) => actif && toast({ title: messageErreur(err), variant: 'destructive' }))
      .finally(() => actif && setChargement(false));

    return () => { actif = false; };
  }, [searchParams, peutConsulterInitiatives]);

  const appliquerFiltres = (e) => {
    e.preventDefault();
    setSearchParams(construireParams(filtres), { replace: true });
  };

  const reinitialiser = () => {
    setFiltres(filtresVides);
    setSearchParams({}, { replace: true });
  };

  // Export comité de pilotage (4F) : PDF ou PowerPoint générés côté
  // serveur (dépenses réelles du registre, prorata = estimation).
  const [exportEnCours, setExportEnCours] = useState('');
  const exporterComite = async (format) => {
    setExportEnCours(format);
    try {
      const nom = await telechargerFichier(
        `/export/comite-pilote?format=${format}`,
        `comite-pilote.${format}`
      );
      toast({ title: t('dashboard.export.succes', { nom }) });
    } catch (err) {
      toast({ title: messageErreurBlob(err), variant: 'destructive' });
    } finally {
      setExportEnCours('');
    }
  };

  const recentes = useMemo(() => {
    const base = initiatives.filter((i) => !i.archive);
    const q = rechercheRecente.trim().toLowerCase();
    if (q) {
      return base.filter((i) =>
        [i.code, i.nom, i.responsable?.nomComplet, i.entite?.nom]
          .filter(Boolean)
          .some((v) => v.toLowerCase().includes(q))
      ).slice(0, 6);
    }
    return base.slice(0, 6);
  }, [initiatives, rechercheRecente]);

  if (chargement) {
    return (
      <div className="min-h-screen bg-background">
        <div className="flex flex-col items-center justify-center h-64 gap-3">
          <div className="animate-pulse rounded-full h-8 w-8 bg-primary" />
          <p className="text-sm text-muted-foreground">{t('common.chargement')}</p>
        </div>
      </div>
    );
  }

  // Données absentes (appel en échec) : message explicite au lieu d'une
  // page blanche — sans ce garde-fou, kpis null ferait echouer le rendu.
  if (!kpis) {
    return (
      <div className="min-h-screen bg-background">
        <div className="flex flex-col items-center justify-center h-64 gap-2">
          <p className="text-sm text-muted-foreground">{t('common.erreurInattendue')}</p>
        </div>
      </div>
    );
  }

  const statuts = kpis.parStatut.filter((d) => d.count > 0);
  const totalStatuts = statuts.reduce((s, d) => s + d.count, 0);
  const priorites = kpis.parPriorite.filter((d) => d.count > 0);
  const enRetard = kpis.initiativesEnRetard ?? [];
  const risquesCritiques = kpis.risquesCritiques ?? [];
  const activite = kpis.activiteRecente ?? [];

  const vide = kpis.totalInitiatives === 0;

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-10 flex h-16 items-center gap-2 px-4 border-b border-border bg-background/95 backdrop-blur">
        <div className="flex-1 flex items-center justify-between">
          <div>
            <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
              {t('dashboard.hero.eyebrow')}
            </span>
            <h1 className="text-xl font-semibold">
              {t('dashboard.hero.bienvenue', { prenom: user?.prenom ?? '' })}
            </h1>
            <p className="text-sm text-muted-foreground">{t('dashboard.hero.sousTitre')}</p>
          </div>
          <div className="flex items-center gap-2">
            {hasPermission('DASHBOARD_CONSULTER') && (
              <>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => exporterComite('pdf')}
                  disabled={exportEnCours !== ''}
                  title={t('dashboard.export.aide')}
                >
                  <IconDownload size={14} className="mr-1.5" />
                  {exportEnCours === 'pdf' ? '…' : t('dashboard.export.pdf')}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => exporterComite('pptx')}
                  disabled={exportEnCours !== ''}
                  title={t('dashboard.export.aide')}
                >
                  <IconDownload size={14} className="mr-1.5" />
                  {exportEnCours === 'pptx' ? '…' : t('dashboard.export.powerpoint')}
                </Button>
              </>
            )}
            <Link to="/roadmap" className="flex items-center gap-2 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90">
              <IconFlag size={14} />
              {kpis.jalonsSemaine === 1
                ? t('dashboard.hero.jalonSemaine', { n: kpis.jalonsSemaine })
                : t('dashboard.hero.jalonsSemaine', { n: kpis.jalonsSemaine ?? 0 })}
              <IconArrowRight size={13} />
            </Link>
            <a href="#surveillance" className="flex items-center gap-2 rounded-md bg-destructive px-3 py-1.5 text-xs font-medium text-destructive-foreground hover:bg-destructive/20">
              <IconAlert size={14} />
              {kpis.initiativesARisque === 1
                ? t('dashboard.hero.initiativeARisque', { n: kpis.initiativesARisque })
                : t('dashboard.hero.initiativesARisque', { n: kpis.initiativesARisque ?? 0 })}
              <IconArrowRight size={13} />
            </a>
          </div>
        </div>
      </header>

      <div className="flex-1 p-4 lg:p-6">
        <form className="mb-6 flex flex-wrap items-end gap-3 bg-card p-4 rounded-xl border border-border shadow-sm" onSubmit={appliquerFiltres} aria-label={t('dashboard.filtre.titre')}>
          <div className="flex-1">
            <Label htmlFor="filtre-statut">{t('dashboard.filtre.ariaStatut')}</Label>
            <select id="filtre-statut" className="flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" value={filtres.statut} onChange={(e) => setFiltres((f) => ({ ...f, statut: e.target.value }))}>
              <option value="">{t('dashboard.filtre.tousStatuts')}</option>
              {referentiels.statuts.map((s) => (
                <option key={s.idStatut} value={s.idStatut}>{ref.statut(s.libelle)}</option>
              ))}
            </select>
          </div>
          <div className="flex-1">
            <Label htmlFor="filtre-entite">{t('dashboard.filtre.ariaEntite')}</Label>
            <select id="filtre-entite" className="flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" value={filtres.entite} onChange={(e) => setFiltres((f) => ({ ...f, entite: e.target.value }))}>
              <option value="">{t('dashboard.filtre.toutesEntites')}</option>
              {referentiels.entites.map((en) => (
                <option key={en.idEntite} value={en.idEntite}>{en.nom}</option>
              ))}
            </select>
          </div>
          <div className="flex-1">
            <Label htmlFor="filtre-responsable">{t('dashboard.filtre.ariaResponsable')}</Label>
            <select id="filtre-responsable" className="flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" value={filtres.responsable} onChange={(e) => setFiltres((f) => ({ ...f, responsable: e.target.value }))}>
              <option value="">{t('dashboard.filtre.tousResponsables')}</option>
              {referentiels.responsables.map((r) => (
                <option key={r.idUser} value={r.idUser}>{r.nomComplet}</option>
              ))}
            </select>
          </div>
          <div className="flex-1">
            <Label className="block text-xs font-medium text-muted-foreground mb-1">{t('dashboard.filtre.periode')}</Label>
            <div className="flex gap-1">
              <input type="date" className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" value={filtres.dateDu} onChange={(e) => setFiltres((f) => ({ ...f, dateDu: e.target.value }))} aria-label={t('dashboard.filtre.du')} />
              <span className="text-xs text-muted-foreground">→</span>
              <input type="date" className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" value={filtres.dateAu} onChange={(e) => setFiltres((f) => ({ ...f, dateAu: e.target.value }))} aria-label={t('dashboard.filtre.au')} />
            </div>
          </div>
          <div className="flex-1 flex items-end">
            <Button type="submit" className="w-full">{t('dashboard.filtre.appliquer')}</Button>
            <Button variant="outline" onClick={reinitialiser} disabled={!filtresActifs} className="w-full">{t('dashboard.filtre.reinitialiser')}</Button>
          </div>
        </form>

        {vide ? (
          <section>
            <Card className="text-center py-12">
              <CardHeader className="flex flex-col items-center justify-center text-center">
                <CardTitle className="text-lg">{filtresActifs ? t('dashboard.filtre.aucunResultat') : t('dashboard.vide.titre')}</CardTitle>
                <CardDescription className="text-muted-foreground">
                  {filtresActifs ? t('dashboard.filtre.aucunResultatTexte') : t('dashboard.vide.texte')}
                </CardDescription>
              </CardHeader>
              {filtresActifs ? (
                <Button variant="outline" onClick={reinitialiser}>{t('dashboard.filtre.reinitialiser')}</Button>
              ) : hasPermission('INITIATIVE_CREER') && (
                <Link to="/initiatives/nouvelle" className="flex items-center gap-2 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90">
                  <IconPlus size={16} /> {t('dashboard.vide.creer')}
                </Link>
              )}
            </Card>
          </section>
        ) : (
          <>
            <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-6" aria-label={t('dashboard.kpi.aria')}>
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">{t('dashboard.kpi.actives')}</CardTitle>
                </CardHeader>
                <CardContent className="flex items-center gap-3">
                  <div className="h-8 w-8 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center">
                    <IconProject size={22} />
                  </div>
                  <span className="text-xl font-semibold">{kpis.totalInitiatives}</span>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">{t('dashboard.kpi.enCours')}</CardTitle>
                </CardHeader>
                <CardContent className="flex items-center gap-3">
                  <div className="h-8 w-8 rounded-lg bg-cyan-100 text-cyan-600 flex items-center justify-center">
                    <IconLayers size={22} />
                  </div>
                  <span className="text-xl font-semibold">{kpis.enCours}</span>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">{t('dashboard.kpi.terminees')}</CardTitle>
                </CardHeader>
                <CardContent className="flex items-center gap-3">
                  <div className="h-8 w-8 rounded-lg bg-green-100 text-green-600 flex items-center justify-center">
                    <IconCheck size={22} />
                  </div>
                  <span className="text-xl font-semibold">{kpis.terminees}</span>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">{t('dashboard.kpi.enRetard')}</CardTitle>
                </CardHeader>
                <CardContent className="flex items-center gap-3">
                  <div className="h-8 w-8 rounded-lg bg-red-100 text-red-600 flex items-center justify-center">
                    <IconAlert size={22} />
                  </div>
                  <span className="text-xl font-semibold">{kpis.enRetard}</span>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">{t('dashboard.kpi.jalonsEnRetard')}</CardTitle>
                </CardHeader>
                <CardContent className="flex items-center gap-3">
                  <div className="h-8 w-8 rounded-lg bg-amber-100 text-amber-600 flex items-center justify-center">
                    <IconFlag size={22} />
                  </div>
                  <span className="text-xl font-semibold">{kpis.jalonsEnRetard ?? 0}</span>
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle className="text-sm">{t('dashboard.kpi.budgetTotal')}</CardTitle>
                </CardHeader>
                <CardContent className="flex items-center gap-3">
                  <div className="h-8 w-8 rounded-lg bg-violet-100 text-violet-600 flex items-center justify-center">
                    <IconWallet size={22} />
                  </div>
                  <span className="text-xl font-semibold">{formatBudget(kpis.budgetTotal, t)}</span>
                </CardContent>
                <CardFooter>
                  <p className="text-xs text-muted-foreground">{t('dashboard.budget.note')}</p>
                </CardFooter>
              </Card>
            </section>

            <section className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
              <div className="lg:col-span-2">
                <Card>
                  <CardHeader>
                    <CardTitle className="text-sm font-semibold">{t('dashboard.etat.titre')}</CardTitle>
                  </CardHeader>
                  <CardContent className="grid grid-cols-2 gap-4">
                    <StatutBarre items={kpis.parStatut} total={totalStatuts} t={t} />
                    <PrioriteBarre items={kpis.parPriorite} total={totalStatuts} t={t} />
                    <AvancementMoyen kpis={kpis} t={t} />
                  </CardContent>
                </Card>
              </div>

              <RecentesSection
                recentes={recentes}
                initiatives={initiatives}
                rechercheRecente={rechercheRecente}
                setRechercheRecente={setRechercheRecente}
                t={t}
                navigate={navigate}
              />
            </section>

            <SurveillerSection enRetard={enRetard} joursDeRetard={joursDeRetard} t={t} navigate={navigate} />

            <RisquesSection risquesCritiques={risquesCritiques} peutConsulterInitiatives={peutConsulterInitiatives} t={t} navigate={navigate} />

            <ActiviteSection activite={activite} peutConsulterInitiatives={peutConsulterInitiatives} t={t} langue={langue} navigate={navigate} />
          </>
        )}
      </div>
    </div>
  );
}
