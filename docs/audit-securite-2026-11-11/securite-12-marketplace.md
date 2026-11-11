# Audit de sécurité 12/13 : module Marketplace (LJ SAAS, branche saas)

Date : 2026-11-11. Périmètre : `backend/src/main/java/tg/parcauto/api/marketplace/**`, les liens avec le PDV (`VenteService`, `restauration/CommandeService`), `config/WebMvcConfig`, `config/SecurityConfig`, et les écrans Angular `pages/boutique-publique/*`, `pages/marketplace/*`, `shared/ticket-pdv.ts`, `shared/bon-cuisine.ts`.
Tests dynamiques : uniquement des GET anonymes sur http://localhost:8085, plus un POST vide rejeté par la validation, donc sans écriture. Lecture seule en base locale `parcauto_saas` pour retrouver deux jetons de suivi existants, masqués ci-dessous. Aucune commande créée, aucun fichier du dépôt modifié.

Je n'ai pas re-signalé ce qui figure déjà dans les audits précédents (socle, plateforme, paramétrage, finance, PDV), sauf quand le défaut réapparaît à un emplacement propre à la marketplace. Dans ce cas, c'est indiqué.

---

## Cartographie des surfaces

| Route | Accès | Cloisonnement |
|---|---|---|
| `GET /api/public/marche` | anonyme, sans limite de débit | SQL direct, toutes entreprises (`MarcheService`) |
| `GET /api/public/boutiques/{slug}` | anonyme | `ContexteBoutiqueInterceptor` : slug filtré par regex, entreprise `ACTIVE`, module requis |
| `GET /api/public/boutiques/{slug}/offres/{id}/photos` | anonyme, sans limite | cloisonnement par l'entreprise du slug (vérifié : l'offre 21 d'une autre boutique renvoie 404) |
| `POST /api/public/boutiques/{slug}/commandes` | anonyme, 10/h par `getRemoteAddr` | lecture seule refusée (POST) |
| `POST /api/public/boutiques/{slug}/locations` | anonyme, 5/h par IP | idem |
| `GET /api/public/suivi/{jeton}` | jeton de 128 bits | entreprise déduite du jeton ; **ni statut ACTIVE ni lecture seule contrôlés** |
| `POST /api/public/suivi/{jeton}/paiement` et `/paiement/simulation` | jeton | idem |
| `/api/marketplace/**` | ADMIN, GERANT, GESTIONNAIRE_PARC (profils du module, sans `@PreAuthorize`) | Hibernate `@TenantId` |
| `/api/pdv/commandes-en-ligne/**` | ADMIN, GERANT, CAISSIER | Hibernate |

Vérifiés : `/api/marketplace/*` et `/api/pdv/commandes-en-ligne` répondent 401 sans jeton. Une requête de pré-vérification CORS depuis `https://evil.example` reçoit 403. Un slug en majuscules ou avec `..%2f` est refusé (404/400). Un jeton malformé ou inconnu renvoie 404.

---

## ÉLEVÉ

### E1. Stock réservé sans limite ni expiration par des commandes anonymes fantômes (vente bloquée)
- **Gravité** : Élevée. CVSS 3.1 indicatif 7.5 (AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H). OWASP A04 (conception non sécurisée) / API6 (accès sans limite à un flux métier sensible).
- **Statut** : confirmé par le code. Pas de test dynamique, puisqu'il faudrait créer des commandes.
- **Emplacements** :
  - `marketplace/CommandeEnLigneService.java:124-125` : les quantités d'une même offre s'additionnent, jusqu'à 50 lignes × 999 = 49 950 unités par offre ;
  - `CommandeEnLigneService.java:434-462` (`reserver`) : sans point de vente, le stock sort **dès la commande**. Pour un produit libre, c'est `retirerStock` ; pour un article dont le PDV n'est pas actif, c'est `stockService.appliquer(... VENTE ...)` ;
  - `CommandeEnLigneService.java:178-181` : une commande en mobile money non payée garde aussi sa réservation ;
  - seul `DemandeLocationService.java:163` a une tâche planifiée (`@Scheduled`). **Aucune expiration des commandes non confirmées ou non payées**, et le stock ne revient que sur annulation manuelle (`liberer`, l.465) ;
  - `BoutiquePubliqueController.java:66-67` : la seule limite est de 10 commandes/heure, avec la clé `"commande:" + getRemoteAddr()`. Elle n'est ni par boutique ni par téléphone.
- **Scénario** :
  1. Un concurrent appelle `GET /api/public/boutiques/{slug}` pour lire `quantiteMax`, le stock exact de chaque offre (voir F2).
  2. Il envoie ensuite **une seule** commande de 50 lignes au maximum avec un faux nom et un faux numéro.
  3. Tout le stock des produits libres passe à 0. La vitrine et la place commune affichent « épuisé » et les vrais clients ne peuvent plus commander, jusqu'à ce que le commerçant repère et annule la commande à la main.
  4. Avec 10 commandes par heure et par IP (rotation d'IP triviale), il recommence dès que le commerçant annule.
  - Avec le point de vente, le stock n'est pas réservé (l.442-448), mais les mêmes requêtes inondent l'écran de caisse (`aTraiterEnCaisse`). Au restaurant, une commande payable à la remise crée immédiatement une commande de salle (l.184-186, `CommandeService.commandeEnLigne`).
  - Derrière un proxy inverse, `getRemoteAddr` est l'IP du proxy : les 10 commandes/heure deviennent un quota **global à toute la plateforme**. Un attaquant bloque alors les commandes de toutes les boutiques avec 10 requêtes. C'est le défaut du socle, mais l'impact propre ici est un déni de vente sur toutes les entreprises.
- **Correctif** :
  - Ne réserver le stock qu'à la confirmation par le commerçant, ou poser une réservation à durée limitée : colonne `reserve_jusqu_a`, avec une tâche `@Scheduled` qui annule les commandes `NOUVELLE` non confirmées, ou `MOBILE_MONEY` non payées, après N minutes, et rend le stock.
  - Plafonner côté serveur la quantité par offre à `min(999, stock, plafond_boutique)` et le total d'unités par commande (ex. 100).
  - Ajouter des limites par boutique et par téléphone normalisé (ex. 3 commandes ouvertes par numéro et par boutique), en plus de l'IP. Calculer l'IP client via `server.forward-headers-strategy=native` et une liste de proxys de confiance.
  - Ajouter un captcha léger (Turnstile, hCaptcha) au-delà de 2 commandes par session.

---

## MOYEN

### M1. Paiement « simulation » activable en production sans garde-fou
- **Gravité** : Moyenne. Elle devient Critique si la variable est posée en production. OWASP A05 (mauvaise configuration).
- **Statut** : confirmé. L'instance locale 8085 tourne en simulation : sur `GET /api/public/suivi/<jeton masqué>`, la commande `CMD-2026-0003` répond `"paiementSimule": true` et `"urlPaiement": "/suivi/<JETON>?paiement=simulation"`.
- **Emplacements** :
  - `marketplace/paiement/PaiementSimule.java:13` : activé par `@ConditionalOnProperty(parcauto.paiement.fournisseur=simulation)` ;
  - `application.yml:38` : `${PAIEMENT_FOURNISSEUR:}` ;
  - `config/GardeSecrets.java:24-57` : ne contrôle pas cette propriété ;
  - `SuiviPublicController.java:43-46` puis `CommandeEnLigneService.java:250-261`.
- **Scénario** : si `PAIEMENT_FOURNISSEUR=simulation` reste dans un `.env` ou un script de déploiement, tout acheteur appelle `POST /api/public/suivi/{son jeton}/paiement/simulation`. Sa commande passe à `PAYEE` et `payeeEnLigne=true`, et un restaurant l'envoie en cuisine (`apresPaiement`). Le commerçant livre une commande « payée en ligne » qui ne l'a jamais été.
- **Correctif** : dans `GardeSecrets`, refuser le démarrage en production si `parcauto.paiement.fournisseur=simulation`. Annoter aussi `PaiementSimule` avec `@Profile("!prod")`, et ajouter un bandeau « DÉMONSTRATION » sur la page de suivi quand `paiementSimule` est vrai.

### M2. Le contrat de confirmation mobile money ne vérifie ni le montant, ni la devise, ni l'origine
- **Gravité** : Moyenne. Constat de conception, **à vérifier au branchement d'un vrai agrégateur**. OWASP A04 / A08 (intégrité).
- **Emplacements** :
  - `marketplace/paiement/PaiementService.java:53-68` (`confirmer`) : seule la référence est comparée à la référence courante **ou à n'importe quelle référence précédente** (`referencesPrecedentes`, l.36-41) ;
  - `FournisseurPaiement.java:9-12` : la documentation prévoit un webhook qui appellerait `confirmer(commande, reference)`.
  - Aujourd'hui, aucun endpoint de webhook n'existe : aucun rappel n'est donc falsifiable pour l'instant.
- **Risques au branchement** :
  - **Montant** : une transaction réussie pour un montant partiel, ou créée à la main chez l'agrégateur avec la même référence marchande, suffit à marquer la commande payée. Rien ne compare `montant_payé` à `commande.getTotal()`.
  - **Origine** : sans signature HMAC ni rappel de vérification, un webhook public serait falsifiable par quiconque connaît une référence. Dans le mode simulation, la référence est même prévisible : `"SIM-" + numero`, et le numéro `CMD-AAAA-NNNN` est séquentiel.
  - **Rejeu** : le double paiement est seulement journalisé (l.61-64). Rien ne garantit l'idempotence par identifiant de transaction.
- **Correctif** : imposer à la signature `confirmer(commande, reference, montant, devise, idTransaction)` :
  - comparer le montant (`compareTo` sur le total) et la devise XOF ;
  - vérifier la signature du webhook (HMAC sur le corps brut, horodatage à moins de 5 minutes) **et** reconfirmer côté serveur par l'API « statut de transaction » de l'agrégateur ;
  - enregistrer `idTransaction` dans une table à contrainte unique, pour l'idempotence ;
  - utiliser une référence marchande aléatoire (`nouveauJeton()`) plutôt que dérivée du numéro.

### M3. Restaurant : une commande payée en ligne passe en salle au prix du catalogue, sans trace du paiement
- **Gravité** : Moyenne. Intégrité financière : double encaissement ou écart de prix. OWASP A04.
- **Statut** : confirmé par le code.
- **Emplacements** :
  - `CommandeEnLigneService.java:158` : prix en ligne = prix propre de l'offre, sinon prix de vente de l'article ;
  - `CommandeEnLigneService.java:191-197` : `ouvrirEnSalle` ne transmet que l'article et la quantité ;
  - `pointdevente/restauration/CommandeService.java:349` : `ligne.setPrixUnitaire(article.getPrixVente())` ;
  - aucune mention de `payeeEnLigne` dans `restauration/*`. Le contrôle « ticket = sous-total » de `venteEncaissee` (l.303) ne vaut que pour la caisse boutique, pas pour la salle.
- **Scénario** :
  1. Le client paie 1 000 F en ligne : l'offre a un prix propre, ou le prix de l'article a changé depuis.
  2. La commande de salle est créée à 1 500 F et ne porte pas l'information « déjà payée », sauf dans une note texte (`noteSalle`, tronquée à 500 caractères).
  3. Au moment de l'encaissement, la caisse fait payer une seconde fois, ou enregistre un mode de paiement fictif. Le chiffre d'affaires est faux, et un caissier peut empocher un paiement en espèces en plus du paiement en ligne.
- **Correctif** :
  - passer le prix de la ligne en ligne à `commandeEnLigne(...)` (comme `prixParArticle` pour la caisse boutique) ;
  - porter un indicateur `payeeEnLigne` et un montant déjà réglé sur la commande de salle. L'encaissement imputerait ce montant à un mode « Paiement en ligne » non modifiable, et n'exigerait que le reste.

### M4. Images de bandeau et d'offres non validées côté serveur, servies anonymement sur la place commune
- **Gravité** : Moyenne. OWASP A04 / A05. Impact croisé entre entreprises sur une page commune.
- **Statut** : confirmé par le code et par les mesures.
- **Emplacements** :
  - `MarketplaceDto.java:36` (`couverture`) et `:75` (`image`) : **aucune** contrainte de taille, de format ou de schéma, contrairement à `Publication.photos` (l.110-112). `Boutique.couverture` et `Offre.image` sont en `text` ;
  - `MarketplaceDto.java:34` et `:70` : `description` sans `@Size` ;
  - `MarcheService.java:214-252` : la place commune renvoie en ligne les 48 bandeaux et logos et les 60 images d'offres ;
  - `OffreService.java:225-235` : la galerie publique renvoie toutes les photos en base64 ;
  - `boutique-publique.html:106`, `marche.html:189`, `offres.html:56` : `[style.background-image]="'url(' + … + ')'"`.
- **Preuves** (anonyme, local) :
  - `GET /api/public/marche` : 200, **815 111 octets** pour 4 boutiques, avec `Cache-Control: no-store` ;
  - `GET /api/public/boutiques/parc-auto` : 484 050 octets, dont un bandeau de 191 175 caractères ;
  - `GET .../parc-auto/offres/21/photos` : 341 990 octets ;
  - aucune limite de débit sur ces trois routes.
- **Scénarios** :
  - **(a) Amplification** : une entreprise, éventuellement malveillante, enregistre un bandeau de plusieurs dizaines de Mo (la limite de 60 Mo par requête vient du socle). Chaque visite anonyme de la place commune, page partagée par toutes les entreprises, renvoie alors des dizaines de Mo. Quelques clients en boucle saturent la bande passante et la mémoire (sérialisation JSON).
  - **(b) Pistage** : `image` ou `couverture` = `https://pisteur.example/p.png`. Chaque visiteur de la place commune, et chaque gérant dans le back-office, charge une ressource tierce qui révèle son IP et son navigateur. Angular bloque `javascript:`, donc pas d'exécution de script.
  - **(c) EXIF** : les photos d'offres ne sont ré-encodées que par le navigateur (canvas). Un appel API direct publie l'image brute, avec ses métadonnées GPS. Sur la base locale, les photos de l'offre 21 n'ont pas d'EXIF : elles sont passées par le front.
- **Correctif** :
  - appliquer à `couverture` et `image` la même contrainte que les photos : `@Size(max = 2_000_000)` et `@Pattern("^data:image/(jpeg|png|webp);base64,…$")`, donc refuser les URL externes ;
  - ré-encoder côté serveur (ImageIO, redimensionnement à 1 600 px de largeur au maximum, sans métadonnées) ;
  - `@Size(max = 5000)` sur les descriptions ;
  - sur la place commune, renvoyer des **URL de vignettes** (`/api/public/images/{id}?v=hash`, avec `Cache-Control: public, max-age=86400` et ETag) plutôt que le base64 en ligne ;
  - limiter le débit par IP de `/marche` et `/photos`.

### M5. Rôles du module trop larges : un gestionnaire de parc fixe les prix de vente du point de vente
- **Gravité** : Moyenne. CVSS indicatif 5.4 (PR:L). OWASP A01 (contrôle d'accès défaillant : séparation des tâches).
- **Statut** : confirmé par le code.
- **Emplacements** :
  - `plateforme/module/ModuleApplicatif.java:52-58` : MARKETPLACE est ouvert à ADMIN, GERANT et GESTIONNAIRE_PARC ;
  - `MarketplaceController.java:33-228` : aucun `@PreAuthorize` par endpoint. Le catalogue PDV, lui, exige `PDV_GESTION = ADMIN, GERANT` (`security/Autorisations.java:21`).
- **Scénario** :
  1. Un compte GESTIONNAIRE_PARC, sans aucun droit sur le PDV, appelle `PUT /api/marketplace/offres/{id}` sur l'offre d'un article de boutique avec `prix: 1`.
  2. Un complice commande en ligne.
  3. À la caisse, le ticket reprend **le prix en ligne** (`VenteService.java:66-68`, puis `PrixCommandesEnLigneMarketplace.prixParArticle`) et le contrôle « ticket = sous-total » passe : l'article sort du stock à 1 F.
  - Ce même compte peut aussi :
    - marquer payée n'importe quelle commande (`POST /commandes/{id}/paiement`, `CommandeEnLigneService.java:412-419`, sans contrôle ni trace du mode) ;
    - annuler des commandes ;
    - lire tout le catalogue PDV avec les stocks (`GET /api/marketplace/sources`).
  - À l'inverse, un GERANT de boutique accepte les demandes de location et réécrit le tarif journalier du parc (`/demandes-location/{id}/accepter`).
- **Correctif** : ajouter des `@PreAuthorize` par famille d'endpoints :
  - offres d'articles et commandes : ADMIN, GERANT ;
  - offres de véhicules et demandes de location : ADMIN, GESTIONNAIRE_PARC ;
  - réglages de boutique et `marquerPayee` : ADMIN.
  - Journaliser `marquerPayee`, avec le motif et le mode de paiement constaté.

---

## FAIBLE

### F1. Suivi par jeton : ni la suspension, ni la lecture seule, ni une durée de vie ne sont appliquées
- **Emplacement** : `ContexteBoutiqueInterceptor.java:70-79`. La branche jeton ne contient ni `e.statut = 'ACTIVE'` ni `abonnements.lectureSeule`, alors que la branche slug les contrôle (l.59-68). La lecture seule exclut aussi `/api/public/**` (`WebMvcConfig.java:37`).
- **Scénario** :
  - Pour une entreprise suspendue ou en lecture seule, `POST /api/public/suivi/{jeton}/paiement` relance encore une transaction mobile money. Le client paie une entreprise qui ne peut plus traiter sa commande.
  - Le jeton n'expire jamais : nom, téléphone, e-mail et adresse de livraison restent consultables indéfiniment par quiconque obtient le lien (copie, capture, historique partagé).
- **Correctif** : dans la branche jeton, refuser les POST si l'entreprise n'est pas `ACTIVE` ou si elle est en lecture seule. Masquer les données personnelles au-delà de 90 jours après un état terminal (ou expirer le jeton), et masquer partiellement le téléphone et l'e-mail dans la réponse publique.

### F2. Fuites d'informations internes dans les réponses publiques
- **Statut** : confirmé par des GET anonymes ou par jeton, jetons masqués.
- **Emplacements** : `MarketplaceDto.java:173-196` (`CommandeReponse`, réutilisé tel quel dans `Suivi`), `:217-229`, `DemandeLocationService.java:197-212`, `OffreService.java:285-290`.
- **Preuves** :
  - suivi de commande : `"id": 3`, `"clientId": 7`, `"articleId": 4`, `"enCaisse": true` et, au besoin, `pdvCommandeId` ;
  - suivi de location : `"camionId": 14`, `"vehicule": "Honda Civic (TG-06xx AQ)"` (**immatriculation**), `recetteId`/`locationId` quand ils sont renseignés ;
  - vitrine : `quantiteMax` = stock exact de chaque article (Lait Nido : 3, Huile : 5…).
- **Impacts** :
  - **Oracle d'existence client** : `trouverOuCreer` rattache la commande à la fiche existante quand nom et téléphone correspondent (`ClientEnLigneService.java:301-303`). Un `clientId` ancien et faible, plutôt que nouveau, révèle qu'une personne est cliente. En plus, la commande s'ajoute à l'historique de cette personne.
  - L'immatriculation de tout véhicule publié s'obtient par une demande de location.
  - Le stock exact est une information concurrentielle, qui sert aussi à calibrer E1.
  - Les identifiants séquentiels révèlent les volumes d'activité.
- **Correctif** :
  - créer un DTO public dédié (`SuiviPublic`) sans `id`, `clientId`, `articleId`, `pdvCommandeId`, `camionId`, `recetteId` ni `locationId`, et avec un libellé de véhicule sans immatriculation ;
  - exposer `quantiteMax = min(stock, plafond)` avec un plafond (ex. 20), ou un simple « en stock / derniers articles » ;
  - ne pas rattacher automatiquement une commande anonyme à une fiche existante : créer une fiche « en ligne » à rapprocher manuellement.

### F3. Recherche de la place commune : `LIKE` non échappé, paramètres non bornés, aucune limite de débit
- **Emplacement** : `MarcheService.java:187` (`"%" + q + "%"` sans échapper `%`, `_` ni `\`), l.205-213 (sous-requête `exists` sur toutes les offres) ; `MarchePublicController.java:24-30` (pas de `@Size`).
- **Preuve** : `GET /api/public/marche?q=%_%_…` (4 500 caractères) renvoie 200 en 12 ms en local. L'impact est faible avec peu de données, mais il croît avec le volume.
- **Correctif** : `@Size(max = 80)` sur `q`, `categorie` et `ville` ; échapper les jokers (`q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")` puis `like ? escape '\'`) ; limiter le débit par IP ; index trigramme si le volume grossit.

### F4. Montants non bornés côté gestion : réapparition locale du BigDecimal géant
- **Emplacement** : `MarketplaceDto.java:46` (`fraisLivraison`), `:72-74` (`prix`, `caution`), `:124` (`tarifJour`, `caution`), `:247` (`Acceptation.tarifJour`). Seul `@PositiveOrZero` est présent, sans `@Digits(integer=14, fraction=2)` ni `@DecimalMax`.
- **Impact** : authentifié. Une erreur 500 survient au dépassement de `numeric(16,2)`, ou au calcul `prix × 49 950` pour une commande anonyme sur une offre au prix maximal. C'est le même défaut que celui signalé en finance.
- **Correctif** : `@Digits(integer = 12, fraction = 2) @DecimalMax("999999999999")` sur ces champs ; plafonner aussi le total d'une commande côté service.

### F5. Galerie publique et place commune incohérentes avec la vitrine
- **Emplacement** : `OffreService.java:225-227` (la galerie ne vérifie que `publiee`, pas `sources.existe()` ni le masquage des offres libres quand le PDV ou le parc est actif) ; `MarcheService.java:233-245` (la place commune liste les offres `LIBRE` que la vitrine masque, l.283).
- **Impact** : les photos d'articles désactivés ou d'offres masquées restent servies anonymement, et les offres affichées ne sont pas commandables.
- **Correctif** : factoriser dans un seul prédicat « offre visible publiquement » utilisé par la vitrine, la galerie, la commande et la place commune (avec son équivalent SQL).

### F6. Identifiant non numérique dans le chemin : erreur 500
- **Preuve** : `GET /api/public/boutiques/boutique-ama/offres/abc/photos` renvoie **500** « Erreur technique du serveur ».
- **Correctif** : traiter `MethodArgumentTypeMismatchException` en 400 dans le gestionnaire global, ce qui évite de polluer les journaux et de fausser la supervision.

---

## INFO

- **I1**
  - Constat : les coordonnées de l'acheteur sont mémorisées dans `localStorage` (`coordonnees-acheteur`, `boutique-publique.ts:313-326`). Sur un appareil partagé (cybercafé, téléphone familial), le client suivant voit nom, téléphone et adresse.
  - Recommandation : demander le consentement (« se souvenir de moi ») ou utiliser `sessionStorage`.
- **I2**
  - Constat : il n'y a pas de `Referrer-Policy` explicite, alors que le jeton de suivi est dans le chemin `/suivi/{jeton}`. Le défaut des navigateurs (`strict-origin-when-cross-origin`) protège déjà les liens wa.me et le fournisseur de paiement.
  - Recommandation : poser `Referrer-Policy: no-referrer` sur le front.
- **I3**
  - Constat : chaque GET de suivi d'une location déclenche un `UPDATE` global sur toutes les entreprises (`DemandeLocationService.java:94`, `expirer()`). La tâche planifiée horaire suffit.
  - Recommandation : retirer l'appel du chemin public.
- **I4**
  - Constat : en caisse boutique, plusieurs commandes peuvent viser la dernière unité, faute de réservation (l.442-448). L'échec n'arrive qu'à l'encaissement.
  - Nature : comportement métier connu, sans enjeu de sécurité.

## Points vérifiés et sains

- Le jeton de suivi fait 128 bits (`SecureRandom`), avec contrainte d'unicité en base, et la regex `^[a-f0-9]{32}$` est appliquée avant la requête SQL. Je n'ai pas trouvé de numéro de suivi devinable : le numéro `CMD-…` n'est jamais une clé publique.
- Le slug passe par une regex stricte et une contrainte `uk_mkt_boutique_slug` : pas de traversée de chemin ni d'usurpation de slug.
- Prix, frais et totaux sont **recalculés côté serveur**. Le client n'envoie que `offreId` et `quantite` (1 à 999), et le contrôle « ticket = sous-total » est fait à la caisse boutique.
- Cloisonnement : les requêtes publiques passent par Hibernate une fois le contexte posé par l'intercepteur (`open-in-view: false`), et une offre d'une autre entreprise renvoie 404. Le SQL direct (`MarcheService`, intercepteur) est entièrement paramétré.
- XSS : aucun `[innerHTML]` ni `bypassSecurityTrust*` sur des données de la marketplace. Le seul `bypassSecurityTrustUrl` porte sur un blob SVG produit par le serveur à partir d'une origine validée. Les tickets et bons imprimés via `doc.write` échappent tous les champs texte (`echapper`), y compris la note du client.
- La suspension est appliquée à la vitrine et à la place commune (`e.statut = 'ACTIVE'`), et les POST de la vitrine sont refusés en lecture seule.
- CORS : une origine inconnue reçoit 403 en pré-vérification. Le module MARKETPLACE est contrôlé sur toutes les routes publiques par entreprise.

---

## Synthèse

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 1 (E1) |
| Moyenne | 5 (M1 à M5) |
| Faible | 6 (F1 à F6) |
| Info | 4 |

**Trois priorités**
1. **E1** : expiration et plafonnement des réservations de stock, et limites par boutique et par téléphone en plus de l'IP.
2. **M1 et M2** : verrouiller le mode simulation en production et durcir dès maintenant le contrat de confirmation du paiement (montant, signature, idempotence) avant tout branchement d'un agrégateur.
3. **M3 et M5** : cohérence financière du restaurant pour les commandes payées en ligne, et `@PreAuthorize` par famille d'endpoints de la marketplace.

**État par surface**
- **Authentification** : correcte. Les routes de gestion répondent 401 sans jeton, et le jeton de suivi est robuste. Seuls la durée de vie et le contrôle de suspension du jeton manquent (F1).
- **Autorisation et cloisonnement** : le cloisonnement entre entreprises est sain. La séparation des rôles internes est trop large (M5).
- **Argent et caisses** : montants recalculés côté serveur. Points faibles : simulation non verrouillée (M1), confirmation sans montant (M2), salle restaurant (M3), bornes des montants (F4).
- **Points publics** : principale surface de risque. Commandes fantômes (E1), amplification par les images (M4), fuites d'informations (F2), recherche (F3).
- **Injection et XSS** : je n'ai trouvé ni injection SQL ni XSS stockée exploitable. Seuls subsistent le `LIKE` non échappé (F3) et des URL d'images externes acceptées (M4b).
- **Infrastructure et configuration** : CORS correct. Il manque une garde de production sur `PAIEMENT_FOURNISSEUR` (M1) et une `Referrer-Policy` (I2).
