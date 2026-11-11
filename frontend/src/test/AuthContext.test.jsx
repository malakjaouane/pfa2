import { describe, it, expect, vi } from 'vitest';
import { estCheminPublic } from '../AuthContext';

vi.mock('../api', () => ({
  default: { get: vi.fn(), post: vi.fn() },
  EVT_SESSION_EXPIREE: 'session-expiree',
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
