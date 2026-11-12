import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import DashboardPage from '../pages/DashboardPage';

/**
 * Test de fumee du tableau de bord.
 *
 * <p>Il couvre les trois regressions qui rendaient la page blanche :</p>
 * <ul>
 *   <li><code>t</code> hors portee dans StatutBarre/PrioriteBarre ;</li>
 *   <li><code>CardDescription</code> utilise sans etre importe ;</li>
 *   <li>prop <code>ref</code> retire par React 18 aux composants
 *       fonctionnels (ref.statut planait).</li>
 * </ul>
 */
const donnees = vi.hoisted(() => ({
  kpis: {
    totalInitiatives: 4,
    enCours: 2,
    enPause: 1,
    terminees: 1,
    enRetard: 1,
    jalonsEnRetard: 0,
    jalonsSemaine: 3,
    initiativesARisque: 1,
    budgetTotal: 500000,
    avancementMoyen: 45,
    parStatut: [
      { libelle: 'En cours', count: 2 },
      { libelle: 'Terminee', count: 1 }
    ],
    parPriorite: [{ libelle: 'Haute', count: 3 }],
    initiativesEnRetard: [
      {
        idInitiative: 9,
        code: 'INIT-009',
        nom: 'Initiative en retard',
        dateFinPrevue: '2026-01-01',
        statut: { libelle: 'En cours' }
      }
    ],
    risquesCritiques: [
      {
        idRisque: 1,
        idInitiative: 9,
        nomInitiative: 'Initiative risquee',
        codeInitiative: 'INIT-009',
        score: 16
      }
    ],
    activiteRecente: []
  },
  initiatives: [
    {
      idInitiative: 9,
      code: 'INIT-009',
      nom: 'Initiative A',
      archive: false,
      statut: { libelle: 'En cours' },
      priorite: { libelle: 'Haute' },
      entite: { nom: 'IT6 Consulting' },
      responsable: { nomComplet: 'Admin Systeme' }
    },
    {
      idInitiative: 10,
      code: 'INIT-010',
      nom: 'Initiative B',
      archive: false,
      statut: { libelle: 'Terminee' },
      priorite: { libelle: 'Basse' },
      entite: { nom: 'IT6 Consulting' }
    }
  ]
}));

vi.mock('../api', () => ({
  default: {
    get: vi.fn((url) => {
      if (url.startsWith('/referentiels/statuts-initiative')) {
        return Promise.resolve({ data: [{ idStatut: 1, libelle: 'En cours' }] });
      }
      if (url.startsWith('/referentiels/entites')) {
        return Promise.resolve({ data: [{ idEntite: 1, nom: 'IT6 Consulting' }] });
      }
      if (url.startsWith('/referentiels/responsables')) {
        return Promise.resolve({ data: [{ idUser: 1, nomComplet: 'Admin Systeme' }] });
      }
      if (url.startsWith('/dashboard')) {
        return Promise.resolve({ data: donnees.kpis });
      }
      if (url.startsWith('/initiatives')) {
        return Promise.resolve({ data: donnees.initiatives });
      }
      return Promise.resolve({ data: [] });
    })
  },
  messageErreur: vi.fn(() => 'erreur'),
  messageErreurBlob: vi.fn(() => 'erreur'),
  telechargerFichier: vi.fn()
}));

vi.mock('../AuthContext', () => ({
  useAuth: () => ({
    user: {
      idUser: 1,
      nom: 'Systeme',
      prenom: 'Admin',
      email: 'admin@it6.ma',
      permissions: ['DASHBOARD_CONSULTER', 'INITIATIVE_CONSULTER', 'INITIATIVE_CREER']
    },
    hasPermission: () => true,
    hasRole: () => true,
    loading: false
  })
}));

vi.mock('../i18n/I18nContext', () => ({
  useI18n: () => ({ t: (k) => k, langue: 'fr' })
}));

vi.mock('../i18n/referentiels', () => ({
  useReferentiels: () => ({
    statut: (v) => v ?? '—',
    priorite: (v) => v ?? '—',
    entite: (v) => v ?? '—'
  })
}));

vi.mock('../i18n/journal', () => ({
  badgeTypeJournal: () => 'journal',
  libelleType: () => 'type',
  texteEvenement: () => 'evenement'
}));

vi.mock('../components/ui/use-toast', () => ({
  useToast: () => ({ toast: vi.fn() })
}));

vi.mock('react-chartjs-2', () => ({
  Doughnut: () => null,
  Bar: () => null
}));

vi.mock('chart.js', () => ({
  Chart: { register: vi.fn() },
  ArcElement: {},
  CategoryScale: {},
  LinearScale: {},
  BarElement: {},
  Tooltip: {},
  Legend: {}
}));

describe('DashboardPage', () => {
  it('se rend completement avec des donnees (barres, sections, filtres)', async () => {
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <DashboardPage />
      </MemoryRouter>
    );

    // Le rendu complet n'arrive qu'apres resolution des appels API.
    expect(await screen.findByText('dashboard.kpi.actives')).toBeInTheDocument();

    // Barres par statut/priorite : exige t() et les referentiels resolus.
    expect(screen.getByText('dashboard.etat.parStatut')).toBeInTheDocument();
    expect(screen.getByText('dashboard.etat.parPriorite')).toBeInTheDocument();

    // Sections a donnees : recentes (CardDescription), surveillance et risques.
    expect(screen.getByText('dashboard.recentes.titre')).toBeInTheDocument();
    expect(screen.getByText('Initiative A')).toBeInTheDocument();
    expect(screen.getByText('Initiative en retard')).toBeInTheDocument();
    expect(screen.getByText('dashboard.risques.titre')).toBeInTheDocument();

    // Formulaire de filtres (referentiels).
    expect(screen.getByLabelText('dashboard.filtre.ariaStatut')).toBeInTheDocument();

    // Aucun etat d'erreur de repli.
    expect(screen.queryByText('common.erreurInattendue')).not.toBeInTheDocument();
  });
});
