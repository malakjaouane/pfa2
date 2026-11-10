import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.jsx';
import { I18nProvider } from './i18n/I18nContext';
import { ToastProvider } from './components/ui/use-toast';
// Migration progressive Phase 3 : styles.css (ancien design) reste importé
// tant que les pages n'ont pas été réécrites en Tailwind/shadcn. Il est placé
// AVANT index.css : à spécificité égale, les règles Tailwind l'emportent, et
// les classes legacy (.btn, .champ, .alerte…) conservent leur spécificité
// supérieure aux sélecteurs d'éléments de Preflight.
import './styles.css';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <I18nProvider>
          <App />
        </I18nProvider>
      </ToastProvider>
    </BrowserRouter>
  </React.StrictMode>
);

// Service worker PWA (enregistré uniquement en production).
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((err) => {
      console.warn('Enregistrement du service worker échoué :', err);
    });
  });
}
