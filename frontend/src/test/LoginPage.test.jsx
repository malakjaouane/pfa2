import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from '../pages/LoginPage';

vi.mock('../AuthContext', () => ({
  useAuth: () => ({
    user: null,
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

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('rend le formulaire de connexion (email, mot de passe, bouton)', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );
    expect(screen.getAllByRole('textbox').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole('button', { name: /login\.seConnecter/i })).toBeInTheDocument();
  });

  it('affiche le lien de mot de passe oublié', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );
    expect(screen.getByText(/login\.oublieLien/i)).toBeInTheDocument();
  });
});
