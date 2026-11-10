import axios from 'axios';

/**
 * Client HTTP de l'API REST.
 *
 * <p>Les requêtes sont authentifiées par les cookies HttpOnly
 * {@code pfa_access} / {@code pfa_refresh} (envoyés automatiquement par le
 * navigateur, même origin via le proxy nginx). {@code withCredentials: true}
 * garantit l'envoi des cookies aussi en développement (Vite sur un port
 * différent du backend).
 *
 * <p>CSRF : avant chaque mutation (POST/PUT/PATCH/DELETE), le cookie
 * {@code XSRF-TOKEN} est lu et renvoyé en header {@code X-XSRF-TOKEN}
 * (Spring Security émet ce cookie via {@code CookieCsrfTokenRepository}).
 * En cas de 403 CSRF, le cookie est rafraîchi (GET /api/auth/csrf) et la
 * requête est rejouée une fois.
 *
 * <p>En cas de 401 (jeton expiré), l'intercepteur tente un
 * {@code POST /api/auth/refresh} (cookie tournant) puis rejoue la requête ;
 * en cas d'échec, l'événement {@link EVT_SESSION_EXPIREE} est émis.
 */
const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
});

/** Lit la valeur d'un cookie depuis document.cookie (XSRF-TOKEN est non-HttpOnly). */
function lireCookie(nom) {
  const correspondance = document.cookie.match('(?:^|;\\s*)' + nom + '=([^;]*)');
  return correspondance ? decodeURIComponent(correspondance[1]) : null;
}

let promesseCsrf = null;

/**
 * S'assure que le cookie XSRF-TOKEN est présent (appel GET /api/auth/csrf
 * sinon). Idempotent : un seul appel en cours à la fois.
 */
function assurerCsrf() {
  if (lireCookie('XSRF-TOKEN')) {
    return Promise.resolve();
  }
  if (promesseCsrf) {
    return promesseCsrf;
  }
  promesseCsrf = api
    .get('/auth/csrf')
    .then(() => {
      promesseCsrf = null;
    })
    .catch(() => {
      promesseCsrf = null;
    });
  return promesseCsrf;
}

api.interceptors.request.use(async (config) => {
  const methode = (config.method || 'get').toUpperCase();
  if (!['GET', 'HEAD', 'OPTIONS'].includes(methode)) {
    await assurerCsrf();
    const jeton = lireCookie('XSRF-TOKEN');
    if (jeton) {
      config.headers['X-XSRF-TOKEN'] = jeton;
    }
  }
  return config;
});

/**
 * Événement émis par l'intercepteur lorsque la session est expirée (401 non
 * recouvrable) : l'état React doit être réinitialisé.
 */
export const EVT_SESSION_EXPIREE = 'pfa:session-expiree';

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status;
    const config = error.config;
    const isLoginCall = config?.url?.includes('/auth/login');
    // L'amorçage CSRF ne doit jamais déclencher de refresh : la requête de
    // refresh passerait par assurerCsrf() qui attend la promesse CSRF en
    // cours → deadlock (aucune mutation ne repartait plus).
    const isCsrfCall = config?.url?.includes('/auth/csrf');

    // 403 CSRF : le cookie XSRF-TOKEN est absent ou invalide -> on le
    // rafraîchit et on rejoue la requête une fois.
    if (status === 403 && !config._retryCsrf && !isLoginCall) {
      config._retryCsrf = true;
      try {
        await api.get('/auth/csrf');
        config.headers['X-XSRF-TOKEN'] = lireCookie('XSRF-TOKEN');
        return api(config);
      } catch {
        // Échec du rafraîchissement CSRF : on laisse l'erreur remonter.
      }
    }

    // 401 : tentative de renouvellement via le cookie de refresh tournant.
    if (status === 401 && !isLoginCall && !isCsrfCall && !config._retryRefresh) {
      config._retryRefresh = true;
      try {
        await api.post('/auth/refresh');
        return api(config);
      } catch {
        // Session expirée : on notifie React via l'événement dédié.
        localStorage.removeItem('pfa_user');
        window.dispatchEvent(new CustomEvent(EVT_SESSION_EXPIREE));
      }
    }

    return Promise.reject(error);
  }
);

/** Extrait un message d'erreur exploitable depuis la réponse API. */
export function messageErreur(error) {
  const data = error.response?.data;
  if (data?.message) {
    if (Array.isArray(data.details) && data.details.length > 0) {
      return `${data.message} — ${data.details.join(' ; ')}`;
    }
    return data.message;
  }
  return error.message || 'Erreur inattendue.';
}

/**
 * Comme {@link messageErreur}, mais adapté aux réponses CSV : en
 * `responseType 'blob'`, le corps d'erreur est un Blob — on le relit pour
 * ne pas afficher un message opaque (« Unexpected token … »).
 */
export async function messageErreurBlob(error) {
  const data = error.response?.data;
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    try {
      return messageErreur({ response: { data: JSON.parse(await data.text()) } });
    } catch {
      /* corps non JSON : on retombe sur le message HTTP */
    }
  }
  return messageErreur(error);
}

/** Nom de fichier annoncé par Content-Disposition (filename* ou filename). */
function nomDepuisEnTete(disposition) {
  if (!disposition) return null;
  const etoile = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (etoile) {
    try { return decodeURIComponent(etoile[1]); } catch { /* encodage invalide */ }
  }
  const simple = /filename="?([^";]+)"?/i.exec(disposition);
  return simple ? simple[1] : null;
}

/**
 * Télécharge un fichier CSV servi par l'API : requête authentifiée en
 * `responseType 'blob'`, nom déduit de l'en-tête Content-Disposition quand
 * le serveur l'expose, sinon nom par défaut. Une panne serveur est
 * rejetée — appeler {@link messageErreurBlob} pour l'afficher.
 */
export async function telechargerFichier(url, nomDefaut) {
  const res = await api.get(url, { responseType: 'blob' });
  const nom = nomDepuisEnTete(res.headers['content-disposition']) || nomDefaut;
  const urlBlob = URL.createObjectURL(new Blob([res.data]));
  try {
    const lien = document.createElement('a');
    lien.href = urlBlob;
    lien.download = nom;
    document.body.appendChild(lien);
    lien.click();
    lien.remove();
  } finally {
    URL.revokeObjectURL(urlBlob);
  }
  return nom;
}

/**
 * Exécute une mutation avec mise à jour optimiste (Phase 3).
 *
 * Le callback `appliquer` est exécuté immédiatement pour mettre à jour
 * l'UI avant la réponse du serveur. Si l'appel API échoue, `annuler` est
 * exécuté pour revenir à l'état précédent, puis l'erreur est rejetée.
 *
 * @param {() => Promise<any>} appel - L'appel API à effectuer.
 * @param {() => void} appliquer - Mise à jour optimiste de l'UI.
 * @param {() => void} annuler - Retour arrière en cas d'échec.
 * @returns {Promise<any>} Le résultat de l'appel API.
 */
export async function withOptimistic(appel, appliquer, annuler) {
  appliquer();
  try {
    return await appel();
  } catch (err) {
    annuler();
    throw err;
  }
}

export default api;
