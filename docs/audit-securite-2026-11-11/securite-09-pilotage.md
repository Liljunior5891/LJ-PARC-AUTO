# Audit de sécurité 9/13 : PILOTAGE (LJ SAAS, branche saas)

Date : 2026-11-11. Audit en lecture seule : aucun fichier du dépôt n'a été modifié, aucun build n'a été lancé.

## Périmètre

Backend `tg.parcauto.api.pilotage` :
- `PilotageController` (`/api/pilotage/**`) ;
- `TableauBordController` (`/api/tableau-bord/**`) ;
- `PilotageService`, `TableauBordService`, `EvolutionService`, `NotificationService` ;
- `RapportService` (CSV), `RapportPdfService`, `MiseEnPagePdf`, `TypeRapportPdf`.

Journal d'activité de l'entreprise : `common/audit/JournalActiviteController|Service|Repository`, `ChangementsJournal`, et le masquage dans `EcouteurAudit`.

Front : `pages/tableau-bord`, `pages/direction`, `pages/vue-parc`, `pages/rapports`, `pages/journal-audit`, `shared/cloche-notifications`, `shared/dessin-vehicule`, ainsi que les gardes de `app.routes.ts`.

Hors périmètre, déjà audités : `pointdevente/pilotage` et `agence/pilotage` (modules PDV et Agences), le socle et le journal général du super-admin.

## Tests dynamiques (local, port 8085, sans authentification)

Les 8 requêtes ci-dessous, faites sans jeton, ont toutes renvoyé **401** :

```
GET /api/pilotage/kpi                                 -> 401
GET /api/pilotage/evolution?mois=999999               -> 401
GET /api/pilotage/rapports/FINANCIER                  -> 401
GET /api/pilotage/rapports-pdf/CAMIONS_DETAIL         -> 401
GET /api/tableau-bord/finances                        -> 401
GET /api/tableau-bord/vue-parc                        -> 401
GET /api/journal-audit                                -> 401
GET /api/pilotage/notifications                       -> 401
```

Je n'ai fait aucun test authentifié. Une connexion écrit une ligne `CONNEXION` dans `journal_activite`, et les règles interdisent d'écrire dans le journal d'audit. Le constat M1 est donc **confirmé par le code** mais **n'a pas été rejoué** (pas de test de charge non plus).

---

## MOYENNE

### M1. Amplification de déni de service : la liste `camionIds` des rapports PDF n'est ni bornée ni dédoublonnée

- **Gravité** : Moyenne. CVSS 3.1 indicatif : 6.5 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:N/A:H). OWASP A04:2021 (conception non sûre), API4:2023 (consommation de ressources sans limite).
- **Statut** : confirmé par lecture du code, non rejoué.
- **Emplacements** :
  - `D:\LJ SAAS\backend\src\main\java\tg\parcauto\api\pilotage\PilotageController.java:134` : `@RequestParam(required = false) List<Long> camionIds` est accepté tel quel ;
  - `D:\LJ SAAS\backend\src\main\java\tg\parcauto\api\pilotage\RapportPdfService.java:175-188` : `camionsDetail` boucle sur chaque identifiant reçu ;
  - `RapportPdfService.java:191-194` : chaque tour appelle `camionService.parId`, `pilotageService.ficheSynthese` **et** `rentabiliteService.ficheCamion` ;
  - `RapportPdfService.java:406-408` : `carburant` boucle de la même façon ;
  - `D:\LJ SAAS\backend\src\main\java\tg\parcauto\api\pilotage\PilotageService.java:313-341` : `ficheSynthese` recharge **toute** la vue du parc (`tableauBordService.vueParc()` : tous les camions, affectations, missions et locations en cours), puis tous les documents à expirer et 100 alertes GPS, pour un seul véhicule ;
  - `D:\LJ SAAS\backend\src\main\java\tg\parcauto\api\finance\rentabilite\RentabiliteService.java:104-108` : `ficheCamion` calcule les distances de **tout le parc** puis filtre sur un camion. Il lance aussi 5 sommes de revenus, les contrats et missions jour par jour, et 3 répartitions.
- **Description** : Spring convertit `camionIds=1,1,1,…` en liste. La limite d'en-tête de Tomcat (8 Ko, aucun réglage dans `application.yml`) laisse passer environ **4 000 identifiants**, éventuellement tous identiques. Aucun `distinct()` ni plafond n'existe : `LIGNES_MAX = 500` ne s'applique qu'à `tousLesCamions()`, quand la liste est vide. Chaque identifiant déclenche une quarantaine de requêtes SQL et deux parcours complets du parc. Le PDF entier (une page par identifiant) est assemblé en mémoire (`ByteArrayOutputStream` de `MiseEnPagePdf`).
- **Scénario** : un gestionnaire de parc (rôle `GESTIONNAIRE_PARC`, pas besoin d'être admin) ou un jeton volé lance quelques requêtes en parallèle :
  `GET /api/pilotage/rapports-pdf/CAMIONS_DETAIL?debut=2021-11-12&fin=2026-11-11&camionIds=7,7,7,…(≈4000 fois)`.
  Chaque requête occupe un thread Tomcat, une connexion Hikari et plusieurs dizaines de Mo de tas pendant de longues minutes. Le pool de connexions s'épuise, ce qui bloque aussi les autres entreprises, dont l'encaissement PDV et les agences. La période de 5 ans autorisée aggrave les boucles jour par jour de `utilisation()`.
- **Correctif** :
  ```java
  // PilotageController.exporterPdf
  List<Long> ids = camionIds == null ? List.of()
          : camionIds.stream().filter(Objects::nonNull).distinct().toList();
  if (ids.size() > 50) {
      throw new BusinessException("50 vehicules au plus par rapport");
  }
  ```
  - Dans `ficheSynthese`, charger la ligne du seul camion (requêtes `affectationCouranteDuCamion`, `missionEnCoursDuCamion`, location active du camion) au lieu de `vueParc()` en entier. Filtrer documents et alertes en SQL par camion.
  - Dans `ficheCamion`, ajouter une requête `distanceDuCamion(camionId, debut, fin)`.
  - Passer les exports PDF et CSV par `common/LimiteurRequetes`, par exemple 10 exports par minute et par utilisateur, et/ou un sémaphore qui limite les éditions simultanées.
  - Plafonner le nombre de pages produites, par exemple 300 pages.

---

## FAIBLE

### F1. Calculs du tableau de direction en O(n²) sur la taille du parc

- **Gravité** : Faible. CVSS indicatif : 4.3 (A:L). OWASP API4:2023.
- **Statut** : confirmé par le code.
- **Emplacement** : `D:\LJ SAAS\backend\src\main\java\tg\parcauto\api\pilotage\PilotageService.java:276-292`.
- **Description** : `kpi()` appelle `rentabiliteService.ficheCamion(...)` (une vingtaine de requêtes, plus les distances de tout le parc) et `carburantService.synthese(...)` pour **chaque** véhicule, juste pour en tirer le taux d'utilisation et la consommation. Pour 300 véhicules sur 5 ans, cela fait environ 7 000 requêtes et 300 parcours complets des distances par appel de `/api/pilotage/kpi`. Le volume est borné par la taille de la flotte de l'entreprise, d'où la gravité faible. Mais l'écran Direction peut être rafraîchi en boucle, et `RapportPdfService.ficheCamion` (M1) reprend ce même chemin.
- **Correctif** : calculer en une requête groupée le taux d'utilisation (jours couverts par contrats et missions) et la consommation (litres et distance par camion), comme le fait déjà `RentabiliteService.global`. Mettre en cache 1 à 5 minutes par entreprise et par période.

### F2. Recherche du journal d'audit : `LIKE` non échappé et parcours complet de la table

- **Gravité** : Faible. CVSS indicatif : 2.7. OWASP A03 (motif `LIKE`), API4.
- **Statut** : confirmé par le code.
- **Emplacement** : `D:\LJ SAAS\backend\src\main\java\tg\parcauto\api\common\audit\JournalActiviteService.java:74-77` (fragment `lister`, lignes du fichier : `motif = "%" + recherche + "%"`). Voir aussi `JournalActiviteRepository.java:9-14` : `select distinct` sur toute la table pour les référentiels.
- **Description** : les jokers `%` et `_` saisis ne sont pas échappés. Ce n'est pas une injection SQL : le critère est paramétré, et le cloisonnement `@TenantId` s'applique, donc aucune fuite entre entreprises. En revanche, `lower(description) like '%…%'` et les deux `distinct` relisent tout le journal de l'entreprise. Or ce journal ne fait que grossir, puisqu'il est immuable. Les paramètres `du` et `au` sont facultatifs et rien ne borne la période. Il faut le rôle ADMIN, d'où la gravité faible.
- **Correctif** :
  - échapper `\`, `%` et `_` puis utiliser `cb.like(..., motif, '\\')` ;
  - imposer une période par défaut (90 jours) quand `recherche` est saisie ;
  - indexer `(entreprise_id, date_action desc)` s'il n'existe pas, et un trigramme `pg_trgm` sur `lower(description)` si la recherche plein texte est voulue ;
  - calculer les référentiels sur une fenêtre récente ou les mettre en cache.

### F3. Masquage des secrets du journal par liste de noms de champs

- **Gravité** : Faible (défense en profondeur). OWASP A09:2021.
- **Statut** : constat de conception. **Aucune fuite actuelle constatée.**
- **Emplacement** : `D:\LJ SAAS\backend\src\main\java\tg\parcauto\api\common\audit\EcouteurAudit.java:68-70` et `:299-300`.
- **Description** : le masquage repose sur une liste figée de noms : `motDePasse`, `pinHash`, `pin`, `token`, `secret`, `cleApi`, `secretQr`, `jeton`, `jetonEmpreinte`, `codeAppairage`, `pinEmpreinte`. J'ai vérifié tous les champs `String` des entités au nom sensible. Les champs secrets présents aujourd'hui sont couverts. `jetonPublic` (`presence/LieuPresence.java:44`) est un identifiant public de QR, et `cle` (`OperationHorsLigne`, `RemiseHorsLigne`) est une clé d'idempotence, pas un secret. Mais un futur champ (`cleSecrete`, `jetonGps`, `motDePasseProvisoire`, `secretWebhook`…) apparaîtrait **en clair** dans le journal. Ce journal est immuable : une fois écrit, il est impossible de purger le secret.
- **Correctif** : créer une annotation `@Secret` (ou `@NonJournalise`) posée sur les champs, lue par `EcouteurAudit`. Ajouter une règle de repli par motif (`(?i).*(motdepasse|password|secret|token|jeton|pin|cle(api|secrete)|hash|empreinte).*`, sauf liste blanche comme `jetonPublic`), et un test qui parcourt les entités et échoue sur tout nom suspect non annoté.

---

## INFO (pas de risque direct, à noter)

### I1. La sélection de plusieurs véhicules est ignorée dans les journaux PDF des recettes et des dépenses

- **Emplacement** : `RapportPdfService.java:569-571`, appelé aux lignes 350, 359, 378 et 387.
- **Description** : `unique(camionIds)` renvoie `null` dès que la liste contient 2 véhicules ou plus. Le rapport porte alors sur **tout le parc** de l'entreprise au lieu de la sélection. Ce n'est pas une fuite entre entreprises (cloisonnement intact), mais c'est un défaut d'intégrité : un rapport présenté comme « 2 véhicules » contient tous les montants.
- **Correctif** : passer une `Collection<Long>` aux services (`r.camion.id in :ids`) ou refuser une sélection multiple pour ces deux rapports.

### I2. La période n'est pas revérifiée dans `RapportPdfService.produire`

- **Emplacement** : `RapportPdfService.java:71-74`.
- **Description** : seul `debut > fin` est revérifié. La borne de 5 ans repose uniquement sur le contrôleur. Il suffit de remplacer ces lignes par `RapportService.exigerPeriode(debut, fin)` pour que tout autre appelant futur soit protégé.

### I3. Aperçu PDF : type du blob non forcé

- **Emplacement** : `D:\LJ SAAS\frontend\src\app\pages\rapports\rapports.ts:150-154`.
- **Description** : `URL.createObjectURL(fichier)` est passé à `bypassSecurityTrustResourceUrl` et affiché en iframe. Le type du blob vient de l'en-tête de réponse. Aujourd'hui, seule une réponse 200 avec `application/pdf` y arrive (les erreurs passent par `error`). Si un proxy ou une erreur renvoyait un jour du HTML en 200, ce HTML s'exécuterait dans l'origine de l'application.
- **Correctif** : `new Blob([fichier], { type: 'application/pdf' })` avant `createObjectURL`, et vérifier `fichier.type` au préalable.

### I4. Troncatures silencieuses à 500 lignes

- **Emplacements** : `RapportService.java:159-160` (CSV « Etat du parc »), `RapportPdfService.java:49`, `RapportPdfService.java:562-566`.
- **Description** : au-delà de 500 véhicules, chauffeurs ou opérations, les rapports sont tronqués sans mention. C'est une limite utile contre le DoS, mais elle doit être signalée dans le document (« 500 premières lignes »).

### I5. Dépendance OpenPDF 2.0.3

- **Emplacement** : `D:\LJ SAAS\backend\pom.xml:86-90`.
- **Description** : je n'ai pas trouvé de CVE exploitable dans l'usage fait ici (génération seule, aucun PDF ni HTML d'entrée analysé). À garder dans la veille des dépendances.

---

## Points vérifiés et conformes

- **Authentification** : les 8 endpoints testés renvoient 401 sans jeton.
- **Rôles** :
  - `PilotageController` et `TableauBordController` portent `@PreAuthorize(Autorisations.GESTION)`, soit `ADMIN` ou `GESTIONNAIRE_PARC` : le chauffeur, le caissier, le serveur, le gérant PDV et les rôles agence sont exclus. Ils portent aussi `@ModuleRequis(GESTION_PARC)` ;
  - `JournalActiviteController` est réservé à `hasRole('ADMIN')` ;
  - côté front, `roleGuard('ADMIN','GESTIONNAIRE_PARC')` protège `/tableau-bord`, `/vue-parc`, `/direction` et `/rapports`.
- **Cloisonnement multi-entreprise** :
  - toutes les requêtes du pilotage sont des JPQL ou des dérivées Spring Data sur des entités `BaseEntity` avec `@TenantId` : `joursImmobilisationSurPeriode`, `compterEnCours`, `affectationsEnCours`, `missionsEnCours`, `locationsActives`, `totalEnLigneSurPeriode`, `totalEncaisseSurPeriode`, `countByCamionIdAndStatutIn` ;
  - les deux seules requêtes natives (`EvolutionService.java:27-38`) filtrent `entreprise_id = :entreprise` avec `ContexteEntreprise.exiger()` ;
  - un `camionId` d'une autre entreprise donne `ResourceNotFoundException`, avec le même message qu'un identifiant inexistant : pas d'énumération possible ;
  - les lignes `PLATEFORME` du journal (`entreprise_id` nul) sont exclues par le filtre de cloisonnement.
- **Injection SQL** : aucune concaténation de saisie dans le SQL ou le JPQL. Le tri et le regroupement sont fixes, et les types de rapport sont des `enum` liés au chemin (une valeur inconnue est rejetée).
- **Périodes et bornes** :
  - `exigerPeriode` (5 ans au plus, début avant fin) s'applique à `kpi`, `synthese`, aux CSV, aux PDF et à `finances` ;
  - `mois` est borné à [1, 60] et `joursAlerte` à [0, 365] ;
  - la taille de page du journal est bornée à [1, 100].
- **Exports CSV** :
  - `RapportService.echapper` (lignes 188-201) préfixe d'une apostrophe toute cellule qui commence par `= + - @ \t \r`, sauf les vrais nombres ;
  - il met entre guillemets les cellules qui contiennent `;`, `"` ou un retour à la ligne ;
  - toutes les cellules passent par `ligne()`.
- **Nom de fichier** (`Content-Disposition`) : construit à partir du nom d'`enum` et de `LocalDate`, sans saisie libre.
- **PDF** : OpenPDF reçoit du texte brut (`Phrase`, `Paragraph`), sans interprétation HTML, image distante ni URL.
- **XSS côté front** :
  - les écrans du pilotage n'ont aucun `[innerHTML]` alimenté par des données serveur ;
  - `DessinVehicule` n'utilise `bypassSecurityTrustHtml` que sur des constantes du code (recherche par clé `DESSINS[...]`) ;
  - les couleurs des graphiques sont des constantes, et `routerLink` reçoit des cibles fixes fournies par le serveur (`/documents`, `/maintenance`…) ;
  - les libellés saisis (documents, alertes, clients) passent par l'interpolation Angular, donc sont échappés.
- **Données sensibles du journal** : les valeurs des champs secrets existants sont remplacées par « (masque) ». L'adresse IP et l'auteur restent visibles par l'administrateur, ce qui est légitime.
- **Notifications** : les requêtes de la cloche sont recalculées côté serveur toutes les 5 minutes par onglet ouvert, ce qui est acceptable.

---

## Synthèse

| Gravité  | Nombre |
|----------|--------|
| Critique | 0 |
| Élevée   | 0 |
| Moyenne  | 1 (M1) |
| Faible   | 3 (F1, F2, F3) |
| Info     | 5 (I1 à I5) |

**3 priorités**

1. **M1** : dédoublonner et plafonner `camionIds` (50), limiter le nombre d'exports par utilisateur, ne plus recharger `vueParc()` ni les distances de tout le parc pour chaque véhicule.
2. **F1** : agréger en SQL le taux d'utilisation et la consommation des KPI, au lieu de faire N appels à `ficheCamion`, et mettre en cache courte durée.
3. **F3 et I1** : remplacer le masquage par liste de noms par une annotation `@Secret` avec un test de garde ; corriger la sélection multiple des rapports des recettes et des dépenses.

**État par surface**

- **Authentification** : conforme (401 sans jeton, vérifié).
- **Autorisation et cloisonnement** : conforme (rôles GESTION et ADMIN, module requis, `@TenantId`, requêtes natives filtrées). Aucune fuite entre entreprises trouvée.
- **Argent et caisses** : sans objet ici (lecture seule, agrégats). Seul I1 touche à l'exactitude d'un rapport.
- **Points publics** : aucun dans ce module.
- **Injection (SQL, CSV, PDF, XSS)** : conforme.
- **Disponibilité** : 1 constat moyen (M1) et 2 faibles (F1, F2).
- **Infra** : rien de propre au pilotage. La limite d'en-tête Tomcat par défaut (8 Ko) borne seulement en partie M1.
