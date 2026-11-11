# Audit sécurité LJ SAAS, module 1/13 : le socle

- **Date** : 2026-11-11
- **Branche** : `saas` (dernier commit 4698cb4)
- **Périmètre** :
  - packages `tg.parcauto.api.security`, `tg.parcauto.api.config` et `tg.parcauto.api.common` (dont `common/audit`) ;
  - pour le contexte, `plateforme/tenant`, `ControleModuleInterceptor` et `LectureSeuleInterceptor` ;
  - côté frontend, `core/auth`, `verrou.service.ts` et les usages de `innerHTML` / `bypassSecurityTrust*` ;
  - la configuration : `application.yml`, `docker-compose.yml` et `scripts/`.
- **Méthode** : revue statique, puis tests dynamiques en lecture seule sur `http://localhost:8085` et lecture seule de la base `parcauto_saas`. Aucune donnée n'a été écrite et aucun fichier du dépôt n'a été modifié.
  - Les tentatives de connexion ont visé une adresse inexistante, donc rien n'a été écrit dans le journal d'audit.
  - La requête avec un jeton forgé était un simple `GET /api/auth/moi`.

Légende : **[CONFIRMÉ]** = prouvé par un test ou sans ambiguïté dans le code ; **[À VÉRIFIER]** = hypothèse tirée du code, non testée (test destructif ou de type DoS exclu).

---

## Synthèse

| Gravité  | Nombre |
|----------|--------|
| Critique | 0 (1 constat Élevé devient Critique si l'API est déployée avec les valeurs par défaut) |
| Élevée   | 2 |
| Moyenne  | 6 |
| Faible   | 9 |
| Info     | 4 |

**Les 3 priorités**

1. Faire échouer le démarrage de l'API **par défaut** quand la clé JWT ou le mot de passe du super-admin sont ceux de développement (logique inversée, voir E-1). Fixer aussi l'algorithme, l'émetteur et l'audience du JWT.
2. Limiter les tentatives sur `/api/auth/login`, par adresse IP et par compte (E-2), et borner la file du journal d'audit.
3. Monter Spring Boot à la dernière 3.4.x ou 3.5.x (Spring Security, Tomcat et Spring Framework, M-5). Ajouter une CSP stricte, réduire l'exposition du jeton (localStorage, déconnexion côté serveur) et revoir le verrou de caisse (M-1, M-2).

**État par surface**

| Surface | État |
|---|---|
| Authentification | Bonne base : JWT relu en base à chaque requête, `uid` et `ver` vérifiés, compte et entreprise suspendus effectifs immédiatement, mot de passe provisoire imposé. Faiblesses : clé par défaut acceptée hors mode « prod » déclaré, aucune limite sur la connexion, pas de révocation à la déconnexion. |
| Autorisation et cloisonnement | Solide dans le socle : `@TenantId` avec les sentinelles AUCUNE (-1) et RACINE (0), qui échoue fermé ; `ContexteEntreprise` toujours effacé en fin de requête ; entreprise tirée du compte relu, jamais du jeton ; table `utilisateur` filtrée explicitement (`findByIdAndEntrepriseId`). Seulement 3 appels à `enModeSysteme`. Le contrôle de module échoue ouvert si l'annotation manque, ce qui est acceptable (voir Info). |
| Argent et caisses (socle) | Le verrou d'écran de caisse est purement côté client, et l'empreinte du PIN est cassable hors ligne (M-2). |
| Points publics | `/api/public/**` est en `permitAll` ; des limiteurs existent sur l'inscription, la boutique et la présence, mais fondés sur `getRemoteAddr` (inadapté derrière un proxy, M-3). Swagger et OpenAPI sont publics (M-4). Multipart de 60 Mo ouvert aux anonymes (M-6, à vérifier). |
| Injection (socle) | Aucune injection SQL dans le socle : JDBC paramétré, Criteria API. `LIKE` non échappé (F-7). XSS Angular : 3 `bypassSecurityTrustHtml`, dont 1 alimenté par le serveur (borne de présence, à couvrir dans l'audit du module présence). |
| Infra | Postgres de `docker-compose.yml` exposé sur toutes les interfaces avec le mot de passe `parcauto` (F-1). Logs en DEBUG par défaut. Pas de configuration TLS ni d'en-têtes CSP et HSTS (F-5). Journaux locaux non ignorés par git (F-8). |

---

## ÉLEVÉE

### E-1 : clé de signature JWT de développement acceptée par défaut ; un jeton super-admin forgé est accepté [CONFIRMÉ en local]

- **Gravité** : Élevée. Critique (CVSS 3.1 indicatif 9,8, `AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H`) dès qu'une instance tourne sans `JWT_SECRET` et sans `PARCAUTO_PRODUCTION=true` ni profil `prod`.
- **Catégorie OWASP** : A02 Cryptographic Failures, A05 Security Misconfiguration, A07.
- **Emplacements** :
  - `backend/src/main/resources/application.yml:42` : `secret: ${JWT_SECRET:Y2hh…bW0=}` (valeur par défaut versionnée) ;
  - `backend/src/main/resources/application.yml:52` : `password: ${PLATEFORME_ADMIN_PASSWORD:Plateforme@2026}` ;
  - `backend/src/main/java/tg/parcauto/api/config/GardeSecrets.java:45-60` : le refus de démarrer ne joue que si `PARCAUTO_PRODUCTION=true` ou si le profil `prod` est actif ; sinon, simple `log.error` ;
  - `backend/src/main/java/tg/parcauto/api/security/JwtService.java:22-27, 51-53` : ni algorithme imposé, ni `iss` ni `aud`.
- **Description** : la clé HMAC par défaut figure dans le dépôt (poussé sur GitHub ; dépôt public ou non, à vérifier). Le garde-fou fonctionne en liste d'exclusion : il faut **penser** à déclarer la production pour qu'il protège. Un oubli suffit pour que quiconque a lu le code fabrique un jeton pour n'importe quel compte. Il faut connaître l'adresse e-mail, l'`uid` et `ver`, mais ce sont de petits entiers faciles à deviner, et l'adresse par défaut du super-admin est `superadmin@plateforme.local`.
- **Scénario** : déploiement avec `docker run -e DB_…` sans `JWT_SECRET`. L'attaquant forge `{"sub":"superadmin@plateforme.local","uid":2,"ver":1}`, signé avec la clé publique du dépôt, et obtient la console `/api/plateforme/**` : entreprises, abonnements, validation de paiements. Même chose avec l'adresse d'un ADMIN d'entreprise pour lire ou écrire toutes ses données.
- **Preuve (locale, 8085)** :
  - le log de démarrage montre `GardeSecrets : SECRETS DE DEVELOPPEMENT EN USAGE : JWT_SECRET … PLATEFORME_ADMIN_PASSWORD est celui par defaut` ;
  - jeton forgé hors de l'application (HS384 signé avec la clé de `application.yml`, `sub` = adresse du super-admin masquée `supe***`, `uid`=2, `ver`=1) :
    ```
    GET /api/auth/moi  Authorization: Bearer <jeton forgé>
    HTTP 200 {"id":2,"email":"(masqué)","nomComplet":"Administrateur Plateforme","roles":["SUPER_ADMIN"],...}
    ```
  - Au passage : la clé de 64 octets donne HS512 à la signature, mais le parseur a accepté **HS384**. L'algorithme n'est pas épinglé.
- **Correctif** :
  1. Inverser la logique de `GardeSecrets` : refuser **toujours** de démarrer avec la clé ou le mot de passe par défaut, sauf autorisation explicite du développement (`PARCAUTO_DEV=true` ou profil `dev`).
  2. Retirer les valeurs par défaut des secrets dans `application.yml` (`secret: ${JWT_SECRET}`, `password: ${PLATEFORME_ADMIN_PASSWORD}`) et les placer dans un `application-dev.yml` non chargé en production.
  3. Dans `JwtService` :
     - générer avec `.signWith(key, Jwts.SIG.HS512)` ;
     - parser avec `Jwts.parser().verifyWith(key).sig().add(Jwts.SIG.HS512)…`, ou vérifier `header.getAlgorithm()` ;
     - ajouter `.issuer("lj-saas")` et `.audience().add("lj-saas-api")`, et les exiger à la lecture (`requireIssuer`, `requireAudience`) ;
     - n'accepter que les clés d'au moins 64 octets aléatoires (`openssl rand -base64 64`).
  4. Utiliser une clé distincte par environnement (8083, 8085, production), pour qu'un jeton de test ne soit pas rejouable ailleurs.
  5. Changer l'adresse par défaut du super-admin (`PLATEFORME_ADMIN_EMAIL` obligatoire).

### E-2 : aucune limite de tentatives sur la connexion par mot de passe [CONFIRMÉ]

- **Gravité** : Élevée. CVSS indicatif 7,5 (`AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N`). OWASP A07 Identification and Authentication Failures, ASVS V2.2.1.
- **Emplacements** :
  - `backend/src/main/java/tg/parcauto/api/security/AuthController.java:55-76` : aucun appel à `LimiteurRequetes` ni de verrouillage de compte ;
  - `UtilisateurPrincipal.isAccountNonLocked()` renvoie toujours `true` (l.69-71 du fichier).
- **Description** : `POST /api/auth/login` accepte un nombre illimité d'essais. La politique de mot de passe exige seulement 8 caractères (`UtilisateurDto.java:16`). Le super-admin a une adresse prévisible. Chaque échec sur un compte **existant** ajoute une ligne `echecConnexion` au journal de l'entreprise visée, par une file d'exécution **non bornée** (`EcritureJournal.java:47`, `Executors.newSingleThreadExecutor`). L'attaquant peut donc aussi noyer le journal d'audit d'une entreprise et faire grossir la mémoire du serveur.
- **Scénario** :
  - attaque par dictionnaire ou *credential stuffing* sur les comptes ADMIN et SUPER_ADMIN ;
  - inondation du journal d'audit d'un concurrent, avec une adresse IP falsifiée (voir M-3).
- **Preuve** : 15 tentatives consécutives sur une adresse inexistante.
  ```
  for i in 1..15: POST /api/auth/login {"email":"inexistant-audit@exemple.invalid","motDePasse":"fauxN"}
  -> 401 401 401 401 401 401 401 401 401 401 401 401 401 401 401   (aucun 429)
  ```
- **Correctif** :
  1. Appeler `limiteur.exiger("login-ip:" + ip, 10, 15 min)` et `limiteur.exiger("login-compte:" + email.toLowerCase(), 5, 15 min)` **avant** `authenticate`. Renvoyer 429 avec un message neutre.
  2. Ajouter un verrouillage progressif par compte (champ `echecs`, `bloqueJusqua`) via `isAccountNonLocked()`, avec un délai exponentiel. Le super-admin mérite un seuil plus strict, voire une double authentification (TOTP).
  3. Borner la file du journal (`new ThreadPoolExecutor(1,1,0,…, new ArrayBlockingQueue<>(10_000), new DiscardOldestPolicy())`) et agréger les échecs répétés : une ligne « N échecs en X min ».
  4. Relever la politique de mot de passe : 12 caractères minimum, vérification contre une liste de mots de passe compromis (ASVS V2.1).

---

## MOYENNE

### M-1 : jeton JWT en localStorage, valable 8 h, sans révocation à la déconnexion ni CSP [CONFIRMÉ]

- **OWASP** : A07 / A05. ASVS V3.3, V3.5.
- **Emplacements** :
  - `frontend/src/app/core/auth/auth.service.ts:37` : `localStorage.setItem(CLE_TOKEN, …)` ;
  - `auth.service.ts:207-209` : `logout()` ne fait que supprimer les clés locales ;
  - `application.yml:43` : `expiration-minutes: 480` ;
  - aucun en-tête `Content-Security-Policy` (test : la réponse de l'API ne contient que `X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control`).
- **Description** : n'importe quelle XSS, y compris dans une dépendance (jQuery, select2, leaflet sont chargés), lit le jeton et l'exfiltre ; il reste valable 8 h. La déconnexion n'invalide rien côté serveur (`versionJetons` n'est pas incrémenté). Sur les postes partagés (caisses, bornes, agences), un jeton copié avant la déconnexion reste utilisable.
- **Correctif** :
  1. Ajouter un `POST /api/auth/logout` qui appelle `utilisateur.invaliderJetons()`. Inconvénient accepté : toutes les sessions du compte se ferment. Variante : liste de révocation par `jti`.
  2. Raccourcir le jeton d'accès (15–30 min) et ajouter un jeton de rafraîchissement en cookie `HttpOnly; Secure; SameSite=Strict`, à rotation.
  3. Servir le frontend avec une CSP stricte (`default-src 'self'; script-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'`), plus `Referrer-Policy: no-referrer`.

### M-2 : verrou de l'écran de caisse uniquement côté client ; empreinte du PIN cassable hors ligne [CONFIRMÉ par lecture du code]

- **OWASP** : A04 Insecure Design / A07. CVSS indicatif 5,7 (accès physique au poste).
- **Emplacements** :
  - `frontend/src/app/core/services/verrou.service.ts:63-77` : empreinte `SHA-256("lj-saas:" + id + ":" + email + ":" + pin)` stockée dans `localStorage['pin.empreinte.<id>']` ;
  - `verrou.service.ts:102-119` : l'état « déverrouillé » est `sessionStorage['pdv.caisse.deverrouillee']='1'` ;
  - `auth.service.ts:207-209` : `logout()` ne supprime pas l'empreinte du PIN ;
  - `backend/.../security/PinController.java:225-230` : la vérification du PIN ne pose aucun état côté serveur ; les endpoints de caisse ne le contrôlent pas.
- **Description** : le PIN « verrouille » l'écran, mais le jeton reste dans `localStorage` et toutes les API restent utilisables.
  - Sur un poste de caisse verrouillé, n'importe qui peut ouvrir les outils de développement et :
    - soit poser `sessionStorage.setItem('pdv.caisse.deverrouillee','1')` ;
    - soit appeler directement l'API avec le jeton.
  - Le sel est connu : identifiant et adresse e-mail. Le PIN fait 4 à 6 chiffres, soit au plus 10^6 essais. Retrouver le PIN depuis l'empreinte prend moins d'une seconde.
  - L'empreinte survit à la déconnexion et à la fermeture du navigateur.
  - La limite de 5 essais du serveur (`PinService`) est donc sans effet sur ce chemin.
- **Correctif** :
  1. Rendre le verrou effectif côté serveur : le déverrouillage par PIN délivre un jeton de session de caisse court (par exemple 5–15 min glissantes), exigé (en-tête `X-Session-Caisse`) par les endpoints d'encaissement et de mouvement d'espèces. Sinon, faire porter au JWT une revendication `verrou` vérifiée par un intercepteur.
  2. Hors ligne : remplacer SHA-256 par un KDF lent (PBKDF2 ≥ 600 000 itérations ou Argon2 en WASM) avec un sel aléatoire. Limiter localement les essais (effacement de l'empreinte après 5 erreurs) et effacer `pin.empreinte.*` à la déconnexion.
  3. Imposer 6 chiffres (`^\d{6}$`) dans `PinController.java:194, 214-215` et `UtilisateurDto.java:418`.

### M-3 : adresse IP du client mal déterminée : `X-Forwarded-For` cru sans contrôle pour l'audit, `getRemoteAddr` pour les limiteurs [CONFIRMÉ par le code]

- **OWASP** : A09 Security Logging and Monitoring Failures / A04.
- **Emplacements** :
  - `backend/.../common/audit/EcritureJournal.java:100-101` : première valeur de `X-Forwarded-For` prise telle quelle ;
  - limiteurs sur `requete.getRemoteAddr()` : `InscriptionController.java:48`, `BoutiquePubliqueController.java:66,77`, `AppairageBorneController.java:42`, `PointagePublicController.java:121` ;
  - aucun `server.forward-headers-strategy` dans `application.yml`.
- **Description** : n'importe quel client écrit l'adresse IP de son choix dans le journal d'audit (`X-Forwarded-For: 1.2.3.4`), ce qui compromet la non-répudiation. À l'inverse, derrière un proxy inverse (nginx, Traefik), `getRemoteAddr()` vaut l'adresse du proxy pour tous les clients :
  - les limiteurs deviennent **globaux** : 5 inscriptions par heure pour toute la plateforme, 10 commandes par heure pour toute la marketplace ;
  - un seul attaquant bloque donc tout le monde (déni de service fonctionnel).
- **Correctif** : configurer `server.forward-headers-strategy: native` avec `server.tomcat.remoteip.internal-proxies` limité à l'adresse du proxy (RemoteIpValve). Ensuite, utiliser **partout** `request.getRemoteAddr()`, y compris dans `EcritureJournal`, et ne plus jamais lire `X-Forwarded-For` à la main.

### M-4 : documentation OpenAPI et Swagger UI publiques [CONFIRMÉ]

- **OWASP** : A05 Security Misconfiguration.
- **Emplacements** : `backend/.../config/SecurityConfig.java:36-38` (`/v3/api-docs/**`, `/swagger-ui/**` en `permitAll`) et `application.yml:54-57`.
- **Preuve** : `GET /v3/api-docs` renvoie 200 avec 422 605 octets, la carte complète de l'API (endpoints plateforme, paramètres, modèles). `GET /swagger-ui/index.html` renvoie 200.
- **Correctif** : en production, `springdoc.api-docs.enabled=false` et `springdoc.swagger-ui.enabled=false`. Si la documentation reste nécessaire, la réserver à `hasRole('SUPER_ADMIN')` et la retirer de `PUBLIC`.

### M-5 : dépendances backend avec des CVE publiées (Spring Boot 3.4.1) [CONFIRMÉ par les versions du jar]

- **OWASP** : A06 Vulnerable and Outdated Components.
- **Emplacement** : `backend/pom.xml:10` (`spring-boot-starter-parent 3.4.1`). Le jar construit embarque :
  - `spring-security-core-6.4.2` ;
  - `spring-core-6.2.1` ;
  - `tomcat-embed-core-10.1.34` ;
  - `jackson-databind-2.18.2` ;
  - `logback-core-1.5.12` ;
  - `postgresql-42.7.4`.
- **CVE pertinentes** :
  - **CVE-2025-22228** (Spring Security 6.4.0–6.4.3) : `BCryptPasswordEncoder.matches` renvoie vrai pour deux mots de passe de plus de 72 octets dont les 72 premiers sont identiques. Ici la limite est `@Size(max=72)` en **caractères**, alors qu'un mot de passe accentué peut dépasser 72 octets. Le login n'impose d'ailleurs aucune limite de taille (`AuthController.java:35-36`).
  - **CVE-2025-22234** : régression de la protection temporelle de `DaoAuthenticationProvider`, corrigée en 6.4.4+.
  - Tomcat 10.1.34 : plusieurs CVE de déni de service corrigées depuis, notamment **CVE-2025-48988** (multipart : nombre de parties illimité) et CVE-2025-31650 / CVE-2025-48989 (HTTP/2). Pertinent avec M-6.
  - Spring Framework 6.2.1 et Spring Security 6.4.2 : **CVE-2025-41249 / CVE-2025-41248**, détection des annotations (`@PreAuthorize`) sur des hiérarchies génériques. Pas de contrôleur générique repéré dans le socle ; à vérifier dans les autres modules.
- **Correctif** :
  - monter `spring-boot-starter-parent` à la dernière 3.4.x, ou mieux 3.5.x maintenue, ce qui embarque Spring Security ≥ 6.4.10, Tomcat ≥ 10.1.4x et Spring Framework ≥ 6.2.11 ;
  - ajouter `@Size(max = 72)` sur `LoginRequest.motDePasse` et contrôler la longueur en **octets** UTF-8 avant l'encodage ;
  - mettre en place `dependency-check-maven` ou `mvn versions:display-dependency-updates` en intégration continue.

### M-6 : téléversements multipart de 60 Mo acceptés sur tout `/api/**`, y compris les chemins publics anonymes [À VÉRIFIER, test DoS exclu]

- **OWASP** : A04 / A05 (consommation de ressources, ASVS V12.1).
- **Emplacement** : `application.yml:23-26` (`max-file-size: 60MB`, `max-request-size: 65MB`, réglage global).
- **Description** : `StandardServletMultipartResolver` analyse le corps (`resolveLazily=false`) pour toute requête multipart qui atteint le `DispatcherServlet`. Les chemins `/api/public/**` sont `permitAll`. Un anonyme peut donc envoyer en boucle des corps de 65 Mo, écrits sur le disque temporaire de Tomcat. Le risque est aggravé par CVE-2025-48988 (M-5).
- **Correctif** :
  - baisser le plafond global (5–10 Mo) ;
  - n'autoriser 60 Mo que sur l'endpoint d'import ZIP, par une `MultipartConfigElement` dédiée ou un contrôle de `Content-Length` dans un filtre par chemin ;
  - `spring.servlet.multipart.resolve-lazily: true` ;
  - limiter aussi `server.tomcat.max-swallow-size` et `max-http-form-post-size`.

---

## FAIBLE

### F-1 : PostgreSQL de `docker-compose.yml` exposé sur toutes les interfaces avec un mot de passe trivial [CONFIRMÉ par le fichier]

- **Emplacement** : `docker-compose.yml:6-10` (`POSTGRES_PASSWORD: parcauto`, `ports: "5433:5432"`, donc `0.0.0.0`). L'API retombe aussi sur `postgres/postgres` (`application.yml:6-7`).
- **Scénario** : poste de développement ou serveur sur un réseau partagé : accès direct à la base avec des identifiants connus. Le conteneur de test réel est bien lié à `127.0.0.1:5434`, mais le fichier versionné ne l'est pas.
- **Correctif** :
  - `ports: ["127.0.0.1:5433:5432"]` ;
  - mot de passe tiré de `.env` (`${POSTGRES_PASSWORD:?obligatoire}`) ;
  - supprimer les valeurs par défaut `DB_USER` et `DB_PASSWORD` dans `application.yml`, et les faire contrôler par `GardeSecrets` ;
  - utiliser un rôle applicatif sans droits de superutilisateur.

### F-2 : énumération des adresses e-mail d'autres entreprises par l'écran des utilisateurs [CONFIRMÉ par le code]

- **Emplacements** : `backend/.../security/UtilisateurService.java:57-59` et `75-79` (« Un compte existe deja avec l'adresse X »). L'unicité de l'adresse est globale, toutes entreprises confondues.
- **Description** : un ADMIN d'entreprise teste si une adresse existe **ailleurs** sur la plateforme. C'est une fuite de données inter-entreprises limitée, qui sert de base à l'attaque E-2.
- **Correctif** : message neutre (« Adresse indisponible »), limitation du nombre de créations ou modifications, et journalisation des refus.

### F-3 : changement de son mot de passe sans limite d'essais sur l'ancien mot de passe [CONFIRMÉ par le code]

- **Emplacement** : `backend/.../security/UtilisateurService.java:154-167`.
- **Scénario** : qui détient un jeton volé (XSS, poste partagé) teste des mots de passe actuels sans limite pour récupérer le mot de passe en clair, puis le réutiliser ailleurs.
- **Correctif** : `limiteur.exiger("mdp:" + id, 5, 15 min)`, puis invalidation des jetons après N échecs.

### F-4 : contournement du mot de passe provisoire par préfixe d'URL trop large [CONFIRMÉ par le code, impact faible]

- **Emplacement** : `backend/.../security/JwtAuthenticationFilter.java:72-75` (`startsWith("/api/auth/")`, `startsWith("/api/utilisateurs/moi")`).
- **Description** : le test par préfixe sur `getRequestURI()` laisse passer tout chemin futur commençant par `/api/utilisateurs/moi…` (par exemple `/api/utilisateurs/moiXxx`), ainsi que `/api/auth/pin` (création du PIN avant le changement de mot de passe). Le `StrictHttpFirewall` bloque bien `..` et `;`, donc pas de traversée.
- **Correctif** : liste blanche exacte, avec `PathPatternRequestMatcher` : `/api/auth/moi`, `/api/utilisateurs/moi`, `/api/utilisateurs/moi/mot-de-passe`.

### F-5 : en-têtes de sécurité incomplets ; pas de TLS ni de HSTS configurés [CONFIRMÉ]

- **Preuve** : la réponse de `GET /api/auth/moi` ne contient que `X-Content-Type-Options`, `X-XSS-Protection: 0`, `Cache-Control`, `X-Frame-Options: DENY`. Il manque `Content-Security-Policy`, `Referrer-Policy`, `Permissions-Policy` et `Strict-Transport-Security` (normalement absent en HTTP).
- **Correctif** : `http.headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")).referrerPolicy(…).httpStrictTransportSecurity(…))`, et TLS terminé au proxy avec HSTS. CORS : retirer `allowCredentials(true)`, inutile avec un jeton Bearer, et la valeur par défaut `http://localhost:4200` (port d'un autre projet) dans `application.yml:45`.

### F-6 : l'intercepteur Angular ajoute le jeton à toute URL, y compris externe [CONFIRMÉ par le code, non exploité aujourd'hui]

- **Emplacement** : `frontend/src/app/core/auth/auth.interceptor.ts:9-13`.
- **Description** : aucun filtrage sur `req.url`. Un futur appel `HttpClient` vers un tiers (géocodage, tuiles, mobile money) enverrait le JWT. Aucun appel de ce genre n'existe aujourd'hui (seuls des liens `wa.me` en `href`).
- **Correctif** : n'ajouter l'en-tête que si `req.url.startsWith(environment.apiUrl)`.

### F-7 : motifs `LIKE` non échappés [CONFIRMÉ par le code]

- **Emplacement** : `backend/.../common/SpecificationUtils.java:24-26`. Le paramètre est bien lié (pas d'injection), mais `%` et `_` saisis par l'utilisateur gardent leur sens de joker.
- **Impact** : correspondances élargies, requêtes coûteuses (`q=%_%_%_…`).
- **Correctif** : échapper `\`, `%` et `_`, puis `cb.like(expr, motif, '\\')`, et borner la longueur de `q` (`@Size(max=100)`).

### F-8 : journaux en DEBUG et fichiers de log locaux non ignorés par git [CONFIRMÉ]

- **Emplacements** :
  - `application.yml:59-61` (`tg.parcauto: DEBUG`) et `application.yml:30-31` (`server.error.include-message: always`). L'impact du second est limité : `GlobalExceptionHandler` intercepte toutes les erreurs.
  - `backend/api-8085.log` et `frontend-4300.log` apparaissent non suivis (`??`) mais **pas** ignorés (`.gitignore`). Un `git add .` les publierait sur GitHub.
- **Correctif** : niveau `INFO` par défaut et `DEBUG` dans un profil `dev` ; ajouter `*.log` au `.gitignore`.

### F-9 : avis npm sur Angular 20.3.28 [CONFIRMÉ par `npm audit --omit=dev`]

- 3 avis modérés et 1 élevé :
  - GHSA-jfgx-762w-99vr : fuite du jeton XSRF de `HttpClient`. Impact faible ici : le mécanisme XSRF par cookie n'est pas utilisé.
  - GHSA-ff3f-86qr-9cv3 et GHSA-62vg-58rm-qff7 : DoS du routeur **en SSR**. Non applicable : pas de SSR.
- **Correctif** : `npm i @angular/{common,core,forms,platform-browser,router,compiler}@^20.3.34` ou plus récent.

---

## INFO

- **I-1 : canal WebSocket `/ws` exposé mais inutilisé par le frontend.**
  - Emplacement : `backend/.../config/WebSocketConfig.java:86-109`. Aucune trace de STOMP ni de SockJS dans `frontend/`.
  - La poignée de main exige l'authentification HTTP, et l'abonnement est filtré par entreprise (`DiffusionEntreprise.appartientA`, correct, sans confusion de préfixe grâce au `"/"`).
  - En revanche, le principal est figé à la connexion : un compte désactivé reste abonné tant que la socket vit.
  - Recommandation : supprimer l'endpoint s'il ne sert pas ; sinon revalider le compte périodiquement et limiter la taille des messages entrants.
- **I-2 : le contrôle de module échoue ouvert.** `ControleModuleInterceptor.java:34-37` laisse passer un contrôleur sans `@ModuleRequis`. Revue faite : les contrôleurs sans `@ModuleRequis` (Auth, Pin, Utilisateurs, Journal, Plateforme, Abonnement, Société, HorsLigne, Module) ont tous un `@PreAuthorize` ou une raison d'être publics. Recommandation : test automatique « tout `@RestController` a `@ModuleRequis` ou une annotation explicite `@HorsModule` ».
- **I-3 : masquage des secrets dans l'audit par nom exact de champ.**
  - `EcouteurAudit.java:68-70` couvre `motDePasse`, `pinHash`, `secretQr`, `jeton`, `jetonEmpreinte`, `pinEmpreinte`, `codeAppairage`.
  - `LieuPresence.jetonPublic` n'est **pas** masqué : à décider selon sa sensibilité, car il est imprimé dans le QR.
  - Recommandation : masquer par motif (`(?i).*(jeton|token|secret|pin|motdepasse|cle).*`) plutôt que par liste.
- **I-4 : points positifs constatés.**
  - JWT : compte relu en base à chaque requête, `uid` comparé, `ver` vérifié ; jeton `alg:none` refusé (test : 401) ; entreprise tirée du compte, jamais du jeton.
  - `ContexteEntreprise.effacer()` en `finally` ; `open-in-view: false` ; sentinelles de cloisonnement qui échouent fermé ; pas d'entreprise d'identifiant 0 en base.
  - CSRF désactivé à juste titre (jeton Bearer, pas de cookie de session) ; CORS par liste d'origines (test : origine hostile refusée en 403).
  - `FichierServi` : type déduit de l'extension, `nosniff`, nom nettoyé dans `Content-Disposition`.
  - Mot de passe provisoire imposé au super-admin ; protection du dernier administrateur ; `SUPER_ADMIN` impossible à attribuer depuis une entreprise.
  - Compteur d'essais de PIN atomique.
  - `navigateByUrl(retour)` dans `login.ts:47-48` : pas de redirection ouverte, car le routeur Angular reste interne.

---

## Annexe : requêtes de test exécutées (toutes en lecture, sur 8085)

```
GET  /api/auth/moi (sans jeton)                     -> 401, en-têtes listés en F-5
GET  /v3/api-docs                                   -> 200, 422605 octets
GET  /swagger-ui/index.html                         -> 200
GET  /actuator/health                               -> 404 (actuator absent)
OPTIONS /api/auth/login  Origin: https://evil.example -> 403
OPTIONS /api/auth/login  Origin: http://localhost:4300 -> 200, ACAO + Allow-Credentials:true
POST /api/auth/login x15 (adresse inexistante)      -> 15 x 401, jamais 429
GET  /api/auth/moi  Bearer <alg:none>               -> 401
GET  /api/auth/moi  Bearer <HS384 forgé, clé de dev> -> 200 SUPER_ADMIN   (E-1)
SQL lecture seule : select id, version_jetons, mot_de_passe_a_changer du super-admin ; min(id) entreprise
npm audit --omit=dev (frontend)                     -> 3 modérés, 1 élevé (F-9)
```

Sources CVE : [Spring – CVE-2025-22228](https://spring.io/security/cve-2025-22228), [OSV – CVE-2025-22228](https://osv.dev/vulnerability/CVE-2025-22228), [GitLab Advisory – spring-security-crypto](https://advisories.gitlab.com/maven/org.springframework.security/spring-security-crypto/CVE-2025-22228/).
