import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import api from '../api';
import { AuthProvider, useAuth, estCheminPublic } from '../AuthContext';

vi.mock('../api', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: { permissions: [] } })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
  },
  EVT_SESSION_EXPIREE: 'pfa:session-expiree',
}));

/**
 * L'assistant d'installation ne doit jamais detourner les pages publiques :
 * sans cette regle, un compte connecte qui clique sur « localhost » atterrit
 * sur /onboarding au lieu de la page d'accueil.
 */
describe('Chemins publics (redirection vers l’assistant d’installation)', () => {
  it('la landing, la connexion, l’inscription et « Découvrir » restent publics', () => {
    expect(estCheminPublic('/')).toBe(true);
    expect(estCheminPublic('/login')).toBe(true);
    expect(estCheminPublic('/inscription')).toBe(true);
    expect(estCheminPublic('/decouvrir')).toBe(true);
    expect(estCheminPublic('/decouvrir/mobile')).toBe(true);
  });

  it('les écrans de l’application ne sont pas publics', () => {
    expect(estCheminPublic('/dashboard')).toBe(false);
    expect(estCheminPublic('/initiatives')).toBe(false);
    expect(estCheminPublic('/onboarding')).toBe(false);
  });
});

/** Bouton qui déclenche la déconnexion exposée par le contexte. */
function Sonde() {
  const { logout } = useAuth();
  return (
    <button type="button" onClick={() => logout()}>
      deconnexion
    </button>
  );
}

describe('Déconnexion', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('révoque la session côté serveur puis efface le profil local', async () => {
    localStorage.setItem('pfa_user', '{"email":"admin@it6.ma"}');

    render(
      <MemoryRouter>
        <AuthProvider>
          <Sonde />
        </AuthProvider>
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole('button'));

    // Sans cet appel serveur, les cookies HttpOnly restent valides et la
    // session survit à la déconnexion.
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/auth/logout'));
    expect(localStorage.getItem('pfa_user')).toBeNull();
  });
});
