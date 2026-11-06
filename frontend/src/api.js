import axios from 'axios';

/**
 * Client HTTP de l'API REST. Le jeton JWT est ajouté automatiquement sur
 * chaque requête ; en cas de réponse 401, la session est réinitialisée.
 */
const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' }
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('pfa_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * Événement émis par l'intercepteur lorsque l'API répond 401 : le token est
 * retiré du stockage, mais l'état React doit aussi être réinitialisé, sinon
 * l'application continue d'afficher l'utilisateur comme connecté.
 */
export const EVT_SESSION_EXPIREE = 'pfa:session-expiree';

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const isLoginCall = error.config?.url?.includes('/auth/login');
    if (status === 401 && !isLoginCall) {
      const etaitConnecte = Boolean(localStorage.getItem('pfa_token'));
      localStorage.removeItem('pfa_token');
      localStorage.removeItem('pfa_user');
      if (etaitConnecte) {
        // Seul AuthContext sait « déconnecter » React : on l'avise.
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

export default api;