import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import OnboardingPage from '../pages/OnboardingPage';

vi.mock('../AuthContext', () => ({
  useAuth: () => ({
    user: { email: 'admin@it6.ma', role: { code: 'ADMIN' } },
    login: vi.fn(),
    logout: vi.fn(),
    rafraichir: vi.fn(),
    loading: false,
    hasPermission: () => false,
    hasRole: () => false,
  }),
}));
vi.mock('../i18n/I18nContext', () => ({
  useI18n: () => ({
    t: (k) => k,
    langue: 'fr',
    langues: [
      { code: 'fr', libelle: 'Français', rtl: false },
      { code: 'en', libelle: 'English', rtl: false },
      { code: 'ar', libelle: 'العربية', rtl: true },
      { code: 'es', libelle: 'Español', rtl: false },
    ],
    changerLangue: vi.fn(),
  }),
}));
vi.mock('../i18n/langues', () => ({
  LANGUES: [
    { code: 'fr', libelle: 'Français', rtl: false },
    { code: 'en', libelle: 'English', rtl: false },
    { code: 'ar', libelle: 'العربية', rtl: true },
    { code: 'es', libelle: 'Español', rtl: false },
  ],
  LANGUE_DEFAUT: 'fr',
}));
vi.mock('../api', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: [] })),
    post: vi.fn(() => Promise.resolve({})),
  },
  messageErreur: vi.fn(() => 'Erreur'),
}));

describe('OnboardingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('affiche les 3 étapes de l’installation', () => {
    render(
      <MemoryRouter initialEntry="/onboarding">
        <OnboardingPage />
      </MemoryRouter>
    );
    expect(screen.getByText(/onboarding\.titre/i)).toBeInTheDocument();
    expect(screen.getAllByText(/onboarding\.etape1/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/onboarding\.suivant/i)).toBeInTheDocument();
  });

  it('le bouton « passer » pose le drapeau : plus de redirection forcée', () => {
    render(
      <MemoryRouter initialEntry="/onboarding">
        <OnboardingPage />
      </MemoryRouter>
    );
    // Sans le drapeau, l'AuthContext renverrait vers /onboarding a chaque
    // chargement : le bouton doit lever ce drapeau.
    fireEvent.click(screen.getByText(/onboarding\.passer/i));
    expect(localStorage.getItem('onboardingDone')).toBe('1');
  });
});
