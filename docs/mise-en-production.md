# Mise en production de LJ SaaS

Liste à suivre le jour du passage en production. Depuis l'audit de sécurité (V78), l'API **refuse de démarrer** tant qu'un secret de développement reste en place : c'est voulu.

## 1. Variables d'environnement de l'API

Ne **jamais** définir `PARCAUTO_DEV`, `SWAGGER_ACTIF` ni `PAIEMENT_FOURNISSEUR=simulation` en production.

| Variable | Valeur en production |
| --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Base PostgreSQL de production |
| `DB_USER`, `DB_PASSWORD` | Rôle applicatif **non superutilisateur et non propriétaire des tables** (voir § 4) |
| `JWT_SECRET` | 64 octets aléatoires au moins : `openssl rand -base64 64`. Propre à ce serveur. La changer déconnecte tout le monde |
| `PRESENCE_CLE_PIN` | Facultatif. Si elle est définie dès le départ, les PIN de présence ne dépendent plus de `JWT_SECRET` (`openssl rand -base64 48`). La changer plus tard invalide les PIN |
| `PLATEFORME_ADMIN_EMAIL` | Adresse réelle et non devinable du super-administrateur |
| `PLATEFORME_ADMIN_PASSWORD` | Mot de passe provisoire fort ; il est à changer à la première connexion |
| `CORS_ORIGINS` | L'adresse publique du site, par exemple `https://app.mondomaine.tg` |
| `PROXIES_DE_CONFIANCE` | Adresse du proxy inverse (nginx, Traefik) en expression régulière, par exemple `172\.18\.0\.\d+`. Sans elle, toutes les requêtes semblent venir du proxy et les limites d'essais deviennent communes à tous |
| `DOCUMENTS_DIR` | Dossier persistant et sauvegardé des justificatifs |
| `PAYGATE_AUTH_TOKEN` | Clé API de l'espace marchand PayGate (§ 2) |
| `PAYGATE_URL` | Ne pas définir (vaut `https://paygateglobal.com`). Une adresse non HTTPS est refusée |
| `SERVER_PORT` | Port interne derrière le proxy |
| `NIVEAU_LOG` | `INFO` (défaut) |

Au démarrage, le journal ne doit contenir **aucune** ligne `SECRETS DE DEVELOPPEMENT`.

## 2. PayGate Global (paiement des abonnements)

1. Dans l'espace marchand PayGate, une fois le compte activé :
   - copier la **clé API** dans `PAYGATE_AUTH_TOKEN` ;
   - déclarer l'**adresse de rappel** : `https://<domaine-de-l-api>/api/public/paygate/abonnements`.
2. Comparer le **guide d'intégration** de l'espace marchand avec ce qu'utilise l'application, et signaler toute différence :
   - demande de paiement : `POST https://paygateglobal.com/api/v1/pay`, champs `auth_token`, `phone_number` (8 chiffres), `amount`, `description`, `identifier`, `network` (`FLOOZ` ou `TMONEY`) ; réponse `status` 0 = enregistrée, 2 = clé refusée, 4 = paramètres invalides, 6 = doublon ;
   - vérification : `POST https://paygateglobal.com/api/v2/status`, champs `auth_token`, `identifier` ; réponse `status` 0 = payé, 2 = en cours, 4 = expiré, 6 = annulé.
3. Relancer l'API. Dans « Mon abonnement », le choix « Payer maintenant (T-Money, Flooz) » apparaît.
4. **Essai réel** avec un petit montant : créer une formule de test à 100 F (console super-admin), la payer depuis une entreprise de test, vérifier que l'abonnement s'ouvre et que la facture est numérotée, puis désactiver la formule de test.
5. Le rappel de PayGate n'est pas signé : l'application ne s'y fie pas et redemande l'état à PayGate. Sans rappel (adresse mal déclarée), une vérification automatique passe toutes les 2 minutes ; le paiement est donc pris en compte au plus tard 2 minutes après sa validation.

## 3. Proxy inverse et HTTPS

- TLS terminé au proxy, avec HSTS (l'API envoie déjà `Strict-Transport-Security` en HTTPS).
- Le proxy transmet `X-Forwarded-For` et `X-Forwarded-Proto` ; son adresse figure dans `PROXIES_DE_CONFIANCE`.
- Servir le frontend avec une politique CSP stricte, par exemple :
  `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src 'self' data: https://*.tile.openstreetmap.org; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'`
  (à ajuster si un écran charge une autre ressource : vérifier la console du navigateur).
- Taille maximale des requêtes au proxy : 70 Mo (l'API refuse elle-même au-delà de 10 Mo, sauf l'import ZIP des produits).

## 4. Base de données

- Port PostgreSQL jamais exposé sur Internet (le `docker-compose.yml` le lie à `127.0.0.1`).
- Mot de passe dans `.env` (`POSTGRES_PASSWORD`, obligatoire).
- Deux rôles :
  - un propriétaire des tables, utilisé seulement pour les migrations Flyway (`SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD`) ;
  - le rôle applicatif (`DB_USER`), sans droit de superutilisateur, avec `SELECT, INSERT, UPDATE, DELETE` sur les tables, et seulement `SELECT, INSERT` sur `agc_journal_audit`, `agc_ecriture`, `agc_ligne_registre` et `journal_activite` (journaux immuables).
- Sauvegarde quotidienne (`pg_dump -Fc`) conservée hors du serveur.

## 5. Avant d'ouvrir

- [ ] Spring Boot monté à la dernière 3.4.x ou 3.5.x (failles publiées dans 3.4.1).
- [ ] `npm audit --omit=dev` sans avis élevé côté frontend.
- [ ] Connexion du super-administrateur et changement de son mot de passe provisoire.
- [ ] Essai PayGate réel (§ 2.4).
- [ ] `docs/audit-securite-2026-11-11/README.md` : relire la section « Restes ».
