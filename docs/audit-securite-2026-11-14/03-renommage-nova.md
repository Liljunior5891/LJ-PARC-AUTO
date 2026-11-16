# Audit sécurité 2026-11-14, sujet 3 : renommage Nova

Date : 2026-11-14. Branche `saas`, changements non commités.

## Périmètre

- `backend/.../security/JwtService.java` : émetteur `nova` / `nova-api`, ancien émetteur toléré.
- `DeuxFacteursService` : libellé TOTP.
- `frontend/src/main.ts` : reprise des clés `localStorage`.
- `frontend/src/index.html` : écran de chargement.
- `frontend/public/sw.js` : service worker.

**Méthode** : revue de code. Le test unitaire `JwtServiceTest` a été lu.

---

## JwtService : l'ancien émetteur `lj-saas` / `lj-saas-api` toléré est sûr

**Statut** : confirmé par revue de code.

**Analyse** (`JwtService.java:63-93`)
- **Signature et algorithme.** La signature HS512 est vérifiée avant toute lecture des revendications. L'algorithme est épinglé.
- **Couples acceptés.** `emetteurAccepte` n'accepte que deux couples stricts, (`nova`, aud ∋ `nova-api`) ou (`lj-saas`, aud ∋ `lj-saas-api`). Un émetteur seul ou une audience seule ne suffit pas.
- **Jeton d'attente de la double authentification.**
  - Le nouveau (aud `nova-deux-facteurs`) ne contient pas `nova-api` : refusé par `lire()`.
  - L'ancien (aud `lj-saas-deux-facteurs`) ne contient pas `lj-saas-api` : refusé aussi.
  - `lireJetonDeuxFacteurs` n'accepte que le nouvel émetteur. Un jeton d'attente ne peut donc pas servir de session.
- **Anciens jetons.** Ils ont été signés avec la même clé, par cette même API. Ils restent soumis à leur `exp` (8 h, `JWT_EXPIRATION_MINUTES`), à la version `ver` (révocation) et au `sid` du verrou d'écran. La tolérance ne crée aucun jeton nouveau et n'élargit aucun droit.
- **Aucun autre émetteur de jetons.** Aucun autre appel à `Jwts.builder` dans le code ne pourrait émettre un jeton `lj-saas` réutilisable.

### I-NOVA-1 : tolérance sans date de fin

**Gravité** : Info.

**Emplacement** : `JwtService.java:84-93`.

**Description** : la tolérance n'a pas de date de fin dans le code. Elle reste inoffensive tant que la clé est la même et que les jetons expirent, mais on risque de l'oublier.

**Correctif**
- Ajouter une borne : n'accepter `lj-saas` que si `iat` est antérieur à la date du renommage (constante `Instant`).
- Ou retirer `ANCIEN_EMETTEUR` à la version suivante, 8 h après la mise en production suffit.
- Le changement de clé JWT prévu à la mise en production (`GardeSecrets`) invalidera de toute façon tous les anciens jetons.

---

## DeuxFacteursService : libellé TOTP `Nova`

**Statut** : RAS.

Seul le libellé `issuer` de l'URI `otpauth://` change. Les secrets et les codes déjà enrôlés restent valables.

---

## main.ts : reprise des clés localStorage

**Statut** : RAS.

**Analyse** (`frontend/src/main.ts:5-24`)
- La liste des clés est fermée : `lj-borne-presence`, `lj-impression-auto`, `lj.version.vue`.
- La reprise ne se fait que si la nouvelle clé est absente, puis l'ancienne est effacée.
- Pas d'évaluation de contenu, pas de clé dynamique.
- Le jeton de borne de présence était déjà en `localStorage` : point antérieur, déjà signalé lors de l'audit du 2026-11-11.

---

## index.html : écran de chargement

**Statut** : RAS. Une remarque Info.

- Pas de script : un bloc `<style>` en ligne et une image locale `nova-detoure.png`.
- Une future CSP stricte, recommandée dans l'audit du socle du 2026-11-11 et toujours absente, devra autoriser ce style en ligne, par exemple avec une empreinte `sha256-…` dans `style-src`. Elle peut aussi le déplacer dans un fichier `.css`.

---

## sw.js : service worker

**Statut** : RAS.

**Analyse** (`frontend/public/sw.js:26-31`)
- **Requêtes ignorées** : les requêtes non GET, celles d'une autre origine, et `/api/**`. En production, `apiUrl = '/api'` est sur la même origine, donc correctement exclu. Les réponses de l'API, jetons et données, ne sont jamais mises en cache.
- **Nouveau nom de cache.** `nova-application-v3` purge les anciens caches à l'activation.
- **Point d'attention.** Si un jour des fichiers servis hors de `/api`, comme `/fichiers/...` (justificatifs, logos), arrivaient sur la même origine, ils seraient mis en cache sur l'appareil.

**Correctif préventif** : limiter la mise en cache à une liste d'extensions statiques (`.js`, `.css`, `.png`, `.svg`, `.woff2`, `.webmanifest`) et à la navigation.
