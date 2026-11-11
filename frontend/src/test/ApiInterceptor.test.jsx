import { describe, it, expect, vi } from 'vitest';
import api from '../api';

/**
 * Garde-fou contre la boucle infinie de refresh : un 401 sur /auth/refresh
 * ne doit pas relancer un refresh (chaque relance échoue à son tour et
 * sature le navigateur comme le backend).
 */
describe('Intercepteur 401 (api.js)', () => {
  it('un échec de refresh ne déclenche pas de nouvelle tentative de refresh', async () => {
    const appels = [];
    api.defaults.adapter = (config) => {
      appels.push(config.url);
      return Promise.reject({
        isAxiosError: true,
        config,
        response: { status: 401, data: {} },
        toJSON: () => ({}),
      });
    };

    await expect(api.get('/auth/me')).rejects.toBeTruthy();

    const refresh = appels.filter((u) => u && u.includes('/auth/refresh'));
    expect(refresh.length).toBe(1);
    // Au total : la requête d'origine, l'amorçage CSRF et le refresh.
    expect(appels.length).toBeLessThanOrEqual(4);
  });

  it('la session expirée est signalée à React', async () => {
    const api2 = api;
    api2.defaults.adapter = (config) =>
      Promise.reject({
        isAxiosError: true,
        config,
        response: { status: 401, data: {} },
        toJSON: () => ({}),
      });

    const evt = new Promise((resolve) => {
      window.addEventListener('pfa:session-expiree', () => resolve(true), { once: true });
    });

    await expect(api2.get('/auth/me')).rejects.toBeTruthy();
    await expect(evt).resolves.toBe(true);
  });
});
