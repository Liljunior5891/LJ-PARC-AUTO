// Service worker de l'application installable (PWA) : il la rend rouvrable sans connexion
// (bons de retrait, file hors ligne des agences) et permet son installation.
// Le reseau passe toujours d'abord : le cache ne sert que lorsqu'il ne repond plus.
// Seuls les fichiers de l'application sont concernes ; l'API, sur une autre origine, n'est jamais mise en cache.
const CACHE = 'lj-saas-application-v2';
// Coquille de l'application, gardee des l'installation : l'icone ouvre l'application meme sans reseau.
const COQUILLE = ['/', '/manifest.webmanifest', '/favicon.ico', '/icones/icone-192.png', '/icones/icone-512.png'];

self.addEventListener('install', (evenement) => {
  evenement.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.addAll(COQUILLE))
      .catch(() => undefined)
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (evenement) => {
  evenement.waitUntil(
    caches.keys()
      .then((noms) => Promise.all(noms.filter((n) => n !== CACHE).map((n) => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (evenement) => {
  const requete = evenement.request;
  const adresse = new URL(requete.url);
  if (requete.method !== 'GET' || adresse.origin !== self.location.origin || adresse.pathname.startsWith('/api')) {
    return;
  }
  evenement.respondWith(
    fetch(requete)
      .then((reponse) => {
        if (reponse.ok) {
          const copie = reponse.clone();
          caches.open(CACHE).then((cache) => cache.put(requete, copie));
        }
        return reponse;
      })
      .catch(async () => {
        const trouve = await caches.match(requete);
        if (trouve) {
          return trouve;
        }
        // Une page de l'application, rouverte sans reseau : l'index la reconstruit.
        if (requete.mode === 'navigate') {
          const accueil = await caches.match('/');
          if (accueil) {
            return accueil;
          }
        }
        return Response.error();
      })
  );
});
