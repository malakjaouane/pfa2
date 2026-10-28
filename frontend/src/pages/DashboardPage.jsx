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
import api, { messageErreur } from '../api';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { useReferentiels } from '../i18n/referentiels';
import { badgeTypeJournal, libelleType, texteEvenement } from '../i18n/journal';
import KpiCard from '../components/KpiCard';
import StatutBadge from '../components/StatutBadge';
import SanteBadge from '../components/SanteBadge';
import ProgressBar from '../components/ProgressBar';
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
  IconClock,
  IconChevronDown,
  IconRoadmap,
  IconInbox
} from '../components/Icons';

ChartJS.register(ArcElement, CategoryScale, LinearScale, BarElement, Tooltip, Legend);

/** Couleurs de statut (cohérentes avec les badges). */
const COULEUR_STATUT = {
  'À planifier': '#8590A2',
  'En cours': '#0C66E4',
  'En pause': '#E56910',
  'Terminée': '#22A06B',
  'Annulée': '#C9372C'
};

/** Couleurs de priorité. */
const COULEUR_PRIORITE = {
  Basse: '#579DFF',
  Moyenne: '#0C66E4',
  Haute: '#E56910',
  Critique: '#C9372C'
};

const BADGE_PRIORITE = {
  Basse: 'badge-gris',
  Moyenne: 'badge-bleu',
  Haute: 'badge-ambre',
  Critique: 'badge-rouge'
};

function PrioriteBadge({ libelle }) {
  const ref = useReferentiels();
  if (!libelle) return <span className="texte-muet">—</span>;
  return <span className={`badge ${BADGE_PRIORITE[libelle] ?? 'badge-gris'}`}>{ref.priorite(libelle)}</span>;
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

/** Lit les filtres du dashboard depuis l'URL (vue partageable). */
const lireDepuisUrl = (params) => ({
  statut: params.get('statut') ?? '',
  entite: params.get('entite') ?? '',
  responsable: params.get('responsable') ?? '',
  dateDu: params.get('dateDu') ?? '',
  dateAu: params.get('dateAu') ?? ''
});

/** Reconstruit la query string des filtres (partagée avec /initiatives). */
const construireParams = (f) => {
  const params = new URLSearchParams();
  if (f.statut) params.set('statut', f.statut);
  if (f.entite) params.set('entite', f.entite);
  if (f.responsable) params.set('responsable', f.responsable);
  if (f.dateDu) params.set('dateDu', f.dateDu);
  if (f.dateAu) params.set('dateAu', f.dateAu);
  return params;
};

/**
 * Tableau de bord (page d'accueil) : synthèse du portefeuille, KPI,
 * état du portefeuille (graphiques), initiatives récentes et à surveiller.
 * Toutes les valeurs proviennent de l'API (/dashboard et /initiatives).
 */
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
  const [erreur, setErreur] = useState('');
  const [rechercheRecente, setRechercheRecente] = useState('');

  const peutConsulterInitiatives = hasPermission('INITIATIVE_CONSULTER');
  const peutConsulterDemandes = user?.permissions?.some((p) =>
    ['DEMANDE_SOUMETTRE', 'DEMANDE_EVALUER'].includes(p)
  );
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

  // Toute modification de l'URL recharge les KPI ET la liste : les indicateurs
  // et les graphiques suivent donc les filtres sélectionnés.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    const suffixe = (() => {
      const query = construireParams(depuisUrl).toString();
      return query ? `?${query}` : '';
    })();

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
      .catch((err) => actif && setErreur(messageErreur(err)))
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

  // Initiatives récentes (l'API trie par identifiant décroissant) + recherche locale.
  const recentes = useMemo(() => {
    const base = initiatives.filter((i) => !i.archive);
    const q = rechercheRecente.trim().toLowerCase();
    const filtrees = q
      ? base.filter((i) =>
          [i.code, i.nom, i.responsable?.nomComplet, i.entite?.nom]
            .filter(Boolean)
            .some((v) => v.toLowerCase().includes(q)))
      : base;
    return filtrees.slice(0, 6);
  }, [initiatives, rechercheRecente]);

  if (chargement) {
    return (
      <div className="page">
        <div className="squelette squelette-hero" />
        <section className="kpi-grid">
          {[0, 1, 2, 3, 4].map((k) => <div key={k} className="squelette squelette-kpi" />)}
        </section>
        <div className="grille-etat">
          <div className="squelette squelette-graphique" />
          <div className="squelette squelette-graphique" />
          <div className="squelette squelette-graphique" />
        </div>
      </div>
    );
  }

  if (erreur) {
    return (
      <div className="page">
        <div className="alerte alerte-erreur">{erreur}</div>
      </div>
    );
  }

  const statuts = kpis.parStatut.filter((d) => d.count > 0);
  const totalStatuts = statuts.reduce((s, d) => s + d.count, 0);
  const priorites = kpis.parPriorite.filter((d) => d.count > 0);
  const enRetard = kpis.initiativesEnRetard ?? [];
  const risquesCritiques = kpis.risquesCritiques ?? [];
  // Journal des modifications : 10 dernières lignes servies par /dashboard.
  const activite = kpis.activiteRecente ?? [];

  const donneesStatut = {
    labels: statuts.map((d) => ref.statut(d.libelle)),
    datasets: [{
      data: statuts.map((d) => d.count),
      backgroundColor: statuts.map((d) => COULEUR_STATUT[d.libelle] ?? '#8590A2'),
      borderWidth: 0,
      hoverOffset: 4
    }]
  };

  const donneesPriorite = {
    labels: priorites.map((d) => ref.priorite(d.libelle)),
    datasets: [{
      data: priorites.map((d) => d.count),
      backgroundColor: priorites.map((d) => COULEUR_PRIORITE[d.libelle] ?? '#0C66E4'),
      borderRadius: 4,
      maxBarThickness: 54
    }]
  };

  const optionsDonut = {
    maintainAspectRatio: false,
    cutout: '66%',
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (ctx) =>
            ` ${ctx.label} : ${ctx.parsed} ${t(ctx.parsed > 1 ? 'dashboard.graphique.initiatives' : 'dashboard.graphique.initiative')}`
        }
      }
    }
  };

  const optionsBarres = {
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (ctx) =>
            ` ${ctx.parsed.y} ${t(ctx.parsed.y > 1 ? 'dashboard.graphique.initiatives' : 'dashboard.graphique.initiative')}`
        }
      }
    },
    scales: {
      y: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: '#EBECF0' } },
      x: { grid: { display: false } }
    }
  };

  return (
    <div className="page">
      {/* En-tête principal — accueil personnalisé */}
      <header className="dashboard-hero">
        <div className="dashboard-hero-texte">
          <span className="dashboard-hero-eyebrow">{t('dashboard.hero.eyebrow')}</span>
          <h1>{t('dashboard.hero.bienvenue', { prenom: user?.prenom ?? '' })}</h1>
          <p>{t('dashboard.hero.sousTitre')}</p>
          <div className="dashboard-hero-chips">
            <Link to="/roadmap" className="hero-chip hero-chip-ambre">
              <IconFlag size={14} />
              {kpis.jalonsSemaine === 1
                ? t('dashboard.hero.jalonSemaine', { n: kpis.jalonsSemaine })
                : t('dashboard.hero.jalonsSemaine', { n: kpis.jalonsSemaine ?? 0 })}
              <IconArrowRight size={13} className="hero-chip-fleche" />
            </Link>
            <a href="#surveillance" className="hero-chip hero-chip-rouge">
              <IconAlert size={14} />
              {kpis.initiativesARisque === 1
                ? t('dashboard.hero.initiativeARisque', { n: kpis.initiativesARisque })
                : t('dashboard.hero.initiativesARisque', { n: kpis.initiativesARisque ?? 0 })}
              <IconArrowRight size={13} className="hero-chip-fleche" />
            </a>
          </div>
        </div>
        <div className="dashboard-hero-actions">
          {hasPermission('INITIATIVE_CREER') && (
            <Link to="/initiatives/nouvelle" className="btn btn-primaire btn-grand">
              <IconPlus size={18} />
              {t('dashboard.hero.nouvelleInitiative')}
            </Link>
          )}
          {peutConsulterInitiatives && (
            <Link to="/roadmap" className="btn btn-secondaire">
              <IconRoadmap size={17} />
              {t('dashboard.hero.raccourciRoadmap')}
            </Link>
          )}
          {peutConsulterDemandes && (
            <Link to="/demandes" className="btn btn-secondaire">
              <IconInbox size={17} />
              {t('dashboard.hero.raccourciDemandes')}
            </Link>
          )}
        </div>
      </header>

      {/* Filtres du portefeuille : les KPI et les graphiques suivent la sélection */}
      <form className="carte filtre-bar" onSubmit={appliquerFiltres} aria-label={t('dashboard.filtre.titre')}>
        <div className="filtre-ligne">
          <select aria-label={t('dashboard.filtre.ariaStatut')}
            value={filtres.statut} onChange={(e) => setFiltres((f) => ({ ...f, statut: e.target.value }))}>
            <option value="">{t('dashboard.filtre.tousStatuts')}</option>
            {referentiels.statuts.map((s) => (
              <option key={s.idStatut} value={s.idStatut}>{ref.statut(s.libelle)}</option>
            ))}
          </select>
          <select aria-label={t('dashboard.filtre.ariaEntite')}
            value={filtres.entite} onChange={(e) => setFiltres((f) => ({ ...f, entite: e.target.value }))}>
            <option value="">{t('dashboard.filtre.toutesEntites')}</option>
            {referentiels.entites.map((en) => (
              <option key={en.idEntite} value={en.idEntite}>{en.nom}</option>
            ))}
          </select>
          <select aria-label={t('dashboard.filtre.ariaResponsable')}
            value={filtres.responsable} onChange={(e) => setFiltres((f) => ({ ...f, responsable: e.target.value }))}>
            <option value="">{t('dashboard.filtre.tousResponsables')}</option>
            {referentiels.responsables.map((r) => (
              <option key={r.idUser} value={r.idUser}>{r.nomComplet}</option>
            ))}
          </select>
          <span className="filtre-periode-libelle">{t('dashboard.filtre.periode')}</span>
          <input type="date" aria-label={t('dashboard.filtre.du')} title={t('dashboard.filtre.du')}
            value={filtres.dateDu} onChange={(e) => setFiltres((f) => ({ ...f, dateDu: e.target.value }))} />
          <span className="filtre-sep" aria-hidden="true">→</span>
          <input type="date" aria-label={t('dashboard.filtre.au')} title={t('dashboard.filtre.au')}
            value={filtres.dateAu} onChange={(e) => setFiltres((f) => ({ ...f, dateAu: e.target.value }))} />
          <button type="submit" className="btn btn-primaire">{t('dashboard.filtre.appliquer')}</button>
          <button type="button" className="btn btn-secondaire" onClick={reinitialiser} disabled={!filtresActifs}>
            {t('dashboard.filtre.reinitialiser')}
          </button>
        </div>
      </form>

      {kpis.totalInitiatives === 0 ? (
        <section className="carte etat-vide">
          <h3>{filtresActifs ? t('dashboard.filtre.aucunResultat') : t('dashboard.vide.titre')}</h3>
          <p className="texte-muet">
            {filtresActifs ? t('dashboard.filtre.aucunResultatTexte') : t('dashboard.vide.texte')}
          </p>
          {filtresActifs ? (
            <button type="button" className="btn btn-secondaire" onClick={reinitialiser}>
              {t('dashboard.filtre.reinitialiser')}
            </button>
          ) : hasPermission('INITIATIVE_CREER') && (
            <Link to="/initiatives/nouvelle" className="btn btn-primaire">
              <IconPlus size={16} /> {t('dashboard.vide.creer')}
            </Link>
          )}
        </section>
      ) : (
        <>
          {/* Cartes KPI */}
          <section className="kpi-grid" aria-label={t('dashboard.kpi.aria')}>
            <KpiCard libelle={t('dashboard.kpi.actives')} valeur={kpis.totalInitiatives} icone={<IconProject size={22} />} color="bleu" />
            <KpiCard libelle={t('dashboard.kpi.enCours')} valeur={kpis.enCours} icone={<IconLayers size={22} />} color="cyan" />
            <KpiCard libelle={t('dashboard.kpi.terminees')} valeur={kpis.terminees} icone={<IconCheck size={22} />} color="vert" />
            <KpiCard libelle={t('dashboard.kpi.enRetard')} valeur={kpis.enRetard} icone={<IconAlert size={22} />} color="rouge" />
            <KpiCard libelle={t('dashboard.kpi.jalonsEnRetard')} valeur={kpis.jalonsEnRetard ?? 0} icone={<IconFlag size={22} />} color="ambre" />
            <KpiCard
              libelle={t('dashboard.kpi.budgetTotal')}
              valeur={formatBudget(kpis.budgetTotal, t)}
              icone={<IconWallet size={22} />}
              color="violet"
              aide={t('dashboard.budget.note')}
            />
          </section>

          {/* Le budget affiché est un montant SAISI, pas un compte de
              dépenses : on le dit explicitement plutôt que de laisser
              l'utilisateur supposer une comptabilité qui n'existe pas. */}
          <p className="texte-muet kpi-note">{t('dashboard.budget.note')}</p>

          {/* État du portefeuille */}
          <section className="section-dashboard">
            <div className="section-dashboard-entete">
              <h3>{t('dashboard.etat.titre')}</h3>
            </div>

            <div className="grille-etat">
              <div className="carte">
                <h3>{t('dashboard.etat.parStatut')}</h3>
                <div className="etat-statut">
                  <div className="graphique-donut">
                    <Doughnut data={donneesStatut} options={optionsDonut} />
                  </div>
                  <ul className="legende-statuts">
                    {statuts.map((d) => (
                      <li key={d.libelle}>
                        <span className="legende-pastille" style={{ background: COULEUR_STATUT[d.libelle] ?? '#8590A2' }} />
                        <span className="legende-libelle">{ref.statut(d.libelle)}</span>
                        <span className="legende-valeur">{d.count}</span>
                        <span className="legende-pct">
                          {totalStatuts ? Math.round((d.count / totalStatuts) * 100) : 0} %
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              <div className="carte">
                <h3>{t('dashboard.etat.parPriorite')}</h3>
                <div className="graphique-conteneur">
                  <Bar data={donneesPriorite} options={optionsBarres} />
                </div>
              </div>

              <div className="carte carte-avancement">
                <h3>{t('dashboard.etat.avancementMoyen')}</h3>
                <div className="avancement-valeur">
                  {kpis.avancementMoyen}<span> %</span>
                </div>
                <ProgressBar valeur={kpis.avancementMoyen} large />
                <ul className="avancement-mini">
                  <li><span>{t('dashboard.kpi.enCours')}</span><strong>{kpis.enCours}</strong></li>
                  <li><span>{t('dashboard.kpi.enPause')}</span><strong>{kpis.enPause}</strong></li>
                  <li><span>{t('dashboard.kpi.terminees')}</span><strong>{kpis.terminees}</strong></li>
                </ul>
              </div>
            </div>
          </section>

          {/* Initiatives récentes */}
          {peutConsulterInitiatives && (
            <section className="section-dashboard">
              <div className="section-dashboard-entete">
                <div>
                  <h3>{t('dashboard.recentes.titre')}</h3>
                  <p className="texte-muet">{t('dashboard.recentes.sousTitre')}</p>
                </div>
                <div className="section-dashboard-actions">
                  <div className="champ-recherche">
                    <IconSearch size={16} />
                    <input
                      type="search"
                      placeholder={t('dashboard.recentes.recherchePlaceholder')}
                      value={rechercheRecente}
                      onChange={(e) => setRechercheRecente(e.target.value)}
                      aria-label={t('dashboard.recentes.rechercheAria')}
                    />
                  </div>
                  <Link to="/initiatives" className="btn btn-secondaire">
                    {t('dashboard.recentes.voirToutes')}
                    <IconArrowRight size={16} />
                  </Link>
                </div>
              </div>

              <div className="carte carte-tableau">
                {recentes.length === 0 ? (
                  <p className="texte-muet etat-vide-ligne">
                    {initiatives.length === 0
                      ? t('dashboard.table.aucune')
                      : t('dashboard.table.aucuneRecherche')}
                  </p>
                ) : (
                  <div className="table-enveloppe">
                    <table className="table">
                      <thead>
                        <tr>
                          <th>{t('dashboard.table.code')}</th>
                          <th>{t('dashboard.table.initiative')}</th>
                          <th>{t('dashboard.table.responsable')}</th>
                          <th>{t('dashboard.table.statut')}</th>
                          <th>{t('dashboard.table.priorite')}</th>
                          <th>{t('dashboard.table.avancement')}</th>
                          <th>{t('dashboard.table.echeance')}</th>
                          <th aria-label={t('dashboard.table.actions')} />
                        </tr>
                      </thead>
                      <tbody>
                        {recentes.map((i) => (
                          <tr
                            key={i.idInitiative}
                            className="ligne-cliquable"
                            onClick={() => navigate(`/initiatives/${i.idInitiative}`)}
                          >
                            <td className="cellule-code">{i.code}</td>
                            <td>
                              <Link to={`/initiatives/${i.idInitiative}`} onClick={(e) => e.stopPropagation()}>
                                {i.nom}
                              </Link>
                              <div className="cellule-sous-texte">{i.entite?.nom ?? '—'}</div>
                            </td>
                            <td>{i.responsable?.nomComplet ?? <span className="texte-muet">{t('dashboard.nonAffecte')}</span>}</td>
                            <td><StatutBadge libelle={i.statut?.libelle} /></td>
                            <td><PrioriteBadge libelle={i.priorite?.libelle} /></td>
                            <td>
                              <div className="cellule-avancement">
                                <ProgressBar valeur={i.avancement} />
                                <span className="texte-muet">{i.avancement ?? 0} %</span>
                              </div>
                            </td>
                            <td className={i.enRetard ? 'cellule-retard' : ''}>
                              {formatDate(i.dateFinPrevue)}
                              {i.enRetard && <span className="badge badge-rouge cellule-badge-retard">{t('dashboard.kpi.enRetard')}</span>}
                            </td>
                            <td className="cellule-action">
                              <IconChevronDown size={16} className="icone-chevron" />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </section>
          )}

          {/* À surveiller */}
          <section className="section-dashboard" id="surveillance">
            <div className="section-dashboard-entete">
              <div className="section-titre-icone">
                <h3>{t('dashboard.surveiller.titre')}</h3>
                {enRetard.length > 0 && <span className="badge badge-rouge">{enRetard.length}</span>}
              </div>
              <p className="texte-muet">{t('dashboard.surveiller.sousTitre')}</p>
            </div>

            {enRetard.length === 0 ? (
              <div className="carte etat-vide-ligne etat-vide-ok">
                <IconCheck size={18} />
                {t('dashboard.surveiller.aucun')}
              </div>
            ) : (
              <div className="surveillance-liste">
                {enRetard.map((i) => (
                  <div
                    key={i.idInitiative}
                    className="surveillance-item"
                    onClick={() => navigate(`/initiatives/${i.idInitiative}`)}
                  >
                    <div className="surveillance-icone">
                      <IconAlert size={18} />
                    </div>
                    <div className="surveillance-corps">
                      <div className="surveillance-titre">
                        <Link to={`/initiatives/${i.idInitiative}`} onClick={(e) => e.stopPropagation()}>
                          {i.nom}
                        </Link>
                        <StatutBadge libelle={i.statut?.libelle} />
                        <SanteBadge sante={i.sante} />
                      </div>
                      <div className="surveillance-meta">
                        <span className="cellule-code">{i.code}</span>
                        <span><IconClock size={13} /> {t('dashboard.surveiller.echeance', { date: formatDate(i.dateFinPrevue) })}</span>
                        <span className="surveillance-retard">{t('dashboard.surveiller.joursRetard', { n: joursDeRetard(i.dateFinPrevue) })}</span>
                        {i.sante?.raison && (
                          <span className="texte-muet">{t(`sante.raison.${i.sante.raison}`, i.sante.parametres ?? {})}</span>
                        )}
                      </div>
                      <div className="surveillance-progression">
                        <ProgressBar valeur={i.avancement} />
                        <span className="texte-muet">{t('dashboard.surveiller.progression', { pct: i.avancement ?? 0, responsable: i.responsable?.nomComplet ?? t('dashboard.nonAffecte') })}</span>
                      </div>
                    </div>
                    <IconArrowRight size={18} className="surveillance-chevron" />
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Risques critiques du registre (santé pondérée ≥ 6/9) :
              même sélection filtrée que les KPI, servie par /dashboard. */}
          {peutConsulterInitiatives && (
            <section className="section-dashboard">
              <div className="section-dashboard-entete">
                <div className="section-titre-icone">
                  <h3>{t('dashboard.risques.titre')}</h3>
                  {risquesCritiques.length > 0 && (
                    <span className="badge badge-rouge">{risquesCritiques.length}</span>
                  )}
                </div>
                <p className="texte-muet">{t('dashboard.risques.sousTitre')}</p>
              </div>

              {risquesCritiques.length === 0 ? (
                <div className="carte etat-vide-ligne etat-vide-ok">
                  <IconCheck size={18} />
                  {t('dashboard.risques.aucun')}
                </div>
              ) : (
                <div className="surveillance-liste">
                  {risquesCritiques.map((r) => (
                    <div
                      key={r.idRisque}
                      className="surveillance-item"
                      onClick={() => navigate(`/initiatives/${r.idInitiative}`)}
                    >
                      <div className="surveillance-icone">
                        <IconAlert size={18} />
                      </div>
                      <div className="surveillance-corps">
                        <div className="surveillance-titre">
                          <Link to={`/initiatives/${r.idInitiative}`} onClick={(e) => e.stopPropagation()}>
                            {r.nomInitiative}
                          </Link>
                          <span className="badge badge-rouge">{t('dashboard.risques.score', { score: r.score })}</span>
                        </div>
                        <div className="surveillance-meta">
                          <span className="cellule-code">{r.codeInitiative}</span>
                          <span>{r.description}</span>
                          {r.responsable && (
                            <span className="texte-muet">
                              {t('dashboard.risques.responsable', { nom: r.responsable.nomComplet })}
                            </span>
                          )}
                        </div>
                      </div>
                      <IconArrowRight size={18} className="surveillance-chevron" />
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}

          {/* Activité récente : les 10 dernières lignes du journal des
              modifications (mêmes droits que la page, INITIATIVE_CONSULTER).
              Ligne = badge de type + événement traduit + auteur, avec le
              lien vers la fiche quand la ligne est rattachée à une
              initiative ; le fil complet vit sur /historique. */}
          <section className="section-dashboard">
            <div className="section-dashboard-entete">
              <div className="section-titre-icone">
                <h3>{t('dashboard.activite.titre')}</h3>
                {activite.length > 0 && <span className="badge badge-gris">{activite.length}</span>}
              </div>
              <p className="texte-muet">{t('dashboard.activite.sousTitre')}</p>
            </div>

            {activite.length === 0 ? (
              <div className="carte etat-vide-ligne etat-vide-ok">
                <IconCheck size={18} />
                {t('dashboard.activite.aucune')}
              </div>
            ) : (
              <div className="carte">
                <ol className="historique">
                  {activite.map((ev) => (
                    <li key={ev.id} className="historique-item">
                      <span className="historique-date">
                        {new Date(ev.dateChangement).toLocaleString(langue, {
                          day: '2-digit', month: 'short', year: 'numeric',
                          hour: '2-digit', minute: '2-digit'
                        })}
                      </span>
                      <span className="historique-transition">
                        <span className={`badge ${badgeTypeJournal(ev.typeObjet)}`}>
                          {libelleType(t, ev.typeObjet)}
                        </span>
                        {texteEvenement(t, ev)}
                        {ev.initiative && (
                          <Link to={`/initiatives/${ev.initiative.idInitiative}`}>
                            {' · '}{ev.initiative.code}
                          </Link>
                        )}
                      </span>
                      <span className="historique-auteur">
                        {ev.utilisateur?.nomComplet ?? t('initiativeDetail.auteurInconnu')}
                      </span>
                    </li>
                  ))}
                </ol>
                <Link to="/historique" className="btn btn-petit btn-secondaire historique-plus">
                  {t('dashboard.activite.voirTout')}
                </Link>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
