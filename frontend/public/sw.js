/**
 * Service worker (PWA).
 *
 * <p>Stratégie de cache :</p>
 * <ul>
 *   <li>les fichiers « hashés » de Vite ({@code /assets/…}) sont immuables :
 *       ils sont servis depuis le cache, puis mis en cache à la première
 *       visite ;</li>
 *   <li>la coquille de l'application ({@code index.html}) et les
 *       navigations sont servies <strong>depuis le réseau en priorité</strong>
 *       — sinon un navigateur ayant déjà installé le service worker
 *       continuerait d'exécuter un ancien bundle après un redéploiement ;</li>
 *   <li>l'API n'est jamais ni servie ni mise en cache (session, CSRF,
 *       données vivantes) ;</li>
 *   <li>la version du cache est purgée à l'activation : un nouveau bundle
 *       ⇒ nouvelle version ⇒ cache anterior supprimé.</li>
 * </ul>
 */
const CACHE_NAME = 'pfa-v2';
const ASSETS = [
  '/',
  '/index.html',
  '/favicon.png',
  '/manifest.json'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(ASSETS))
      // Prend la main immédiatement : l'ancien service worker ne continue
      // pas de servir l'ancienne version pendant le prochain chargement.
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const requete = event.request;
  const url = new URL(requete.url);

  // Ni l'API, ni le script du service worker lui-même : laissés au réseau.
  if (url.origin !== self.location.origin) {
    return;
  }
  if (url.pathname.startsWith('/api/') || url.pathname === '/sw.js') {
    return;
  }

  // Navigation : réseau d'abord (index.html à jour), cache en secours.
  if (requete.mode === 'navigate') {
    event.respondWith(
      fetch(requete)
        .then((reponse) => {
          const copie = reponse.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put('/index.html', copie));
          return reponse;
        })
        .catch(() => caches.match('/index.html'))
    );
    return;
  }

  // Assets hashés : cache d'abord, réseau ensuite (et mise en cache).
  event.respondWith(
    caches.match(requete).then((cache) => {
      if (cache) {
        return cache;
      }
      return fetch(requete).then((reponse) => {
        if (reponse.ok && reponse.type === 'basic') {
          const copie = reponse.clone();
          caches.open(CACHE_NAME).then((c) => c.put(requete, copie));
        }
        return reponse;
      });
    })
  );
});
