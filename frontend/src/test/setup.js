import '@testing-library/jest-dom';

/**
 * jsdom n'implémente pas matchMedia, utilisé par le hook de thème pour
 * détecter la préférence « thème sombre » du système. Les navigateurs réels
 * l'exposent tous : le polyfill est ici uniquement pour les tests.
 */
if (!window.matchMedia) {
  window.matchMedia = (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  });
}
