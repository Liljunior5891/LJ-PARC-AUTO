# Audit de sécurité 8/13 : module GPS (LJ SAAS, branche saas)

Date : 2026-11-11. Périmètre : `backend/src/main/java/tg/parcauto/api/gps/**` (traceurs, réception publique par jeton, positions, trajets, géorepères, alertes, veille, diffusion STOMP), `config/WebSocketConfig.java`, `plateforme/tenant/DiffusionEntreprise.java`, et côté Angular `pages/carte-parc/*`, `shared/carte-camion.ts`, `shared/picto-vehicule.ts`, `core/services/gps.service.ts`.

Méthode : revue statique ; tests HTTP locaux en lecture seule sur `http://localhost:8085` (compte de test ADMIN de l'entreprise 13 « essai-parc », véhicules et positions de l'entreprise 1) ; mesure de coût CPU d'un `BigDecimal` reproduite **hors ligne** dans une JVM isolée (scratchpad), sans rien envoyer à l'API. Aucun jeton GPS n'existe dans la base locale (`gps_jeton` vide) et en créer un serait une écriture : la réception publique avec un jeton valide n'a donc pas été rejouée, ses défauts sont établis par le code.

Rappels déjà connus, non re-signalés : clé JWT de dev, Swagger public, envois de 60 Mo, points publics GPS qui ignorent la suspension et la lecture seule, tri sur champs internes (500), DoS `BigDecimal` du module maintenance, canal `/incidents`.

---

## Élevée

### E1. DoS CPU par un nombre à exposant géant dans les adaptateurs de traceurs. Confirmé.
- **Gravité** : Élevée. CVSS 3.1 indicatif 6.5 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:N/A:H). OWASP A04 Insecure Design, ASVS V5.1.
- **Emplacement** :
  - `gps/integration/AdaptateurGenerique.java:54-56` : `new BigDecimal(noeud.get(champ).asText())`, sans borne d'exposant ni de longueur ;
  - `gps/integration/AdaptateurTraccar.java:51,58-59` : `vitesseNoeuds.multiply(NOEUD_EN_KMH).setScale(2, RoundingMode.HALF_UP)` ;
  - `gps/GpsPublicController.java:42` et `gps/GpsController.java:48` : `convertir()` s'exécute **avant** le contrôle `LOT_MAX` (`position/PositionGpsService.java:59`).
- **Description** : un champ envoyé en chaîne JSON, par exemple `"speed":"1e-100000000"`, devient un `BigDecimal` d'échelle 10^8. Le `setScale(2)` calcule alors 10^100000000 en `BigInteger`. Le contrôle `coordonneesValides()` ne s'applique qu'à lat/lon et laisse passer ces valeurs : `1e-100000000` vaut 0 en `double`, donc il est même jugé valide en latitude. La conversion de tout le tableau a lieu dans le fil du contrôleur avant la limite de 1000 positions : une seule requête peut contenir des milliers d'éléments piégés.
- **Preuve** (mesure hors ligne, JDK local, même code que l'adaptateur Traccar) :
  ```
  1e-2000000   valid=true -> 0.00 in   241 ms
  1e-20000000  valid=true -> 0.00 in  5649 ms
  1e-100000000 valid=true -> 0.00 in 58037 ms
  ```
  Soit environ 58 s de CPU pour 16 octets de charge, multipliés par le nombre d'éléments du tableau. Quelques requêtes immobilisent les 200 fils Tomcat.
- **Scénario** : quiconque détient le jeton GPS de l'entreprise (voir E2 : un boîtier, une application Traccar Client, un installateur, une URL vue dans un journal) envoie `POST /api/public/gps/positions/traccar?jeton=…` avec `[{"deviceId":"x","latitude":0,"longitude":0,"speed":"1e-100000000"}, …×1000]`. L'API de **toutes les entreprises** devient indisponible. Le limiteur (1200/min) n'empêche rien : 5 requêtes suffisent. L'ADMIN peut faire de même via `/api/gps/positions/{fournisseur}`.
- **Correctif** :
  1. Dans `AdaptateurGenerique.decimal()`, refuser tout texte qui ne correspond pas à `^-?\d{1,7}(\.\d{1,9})?$`, ou lire `asDouble()`, rejeter `NaN`/`Infinity`, puis `BigDecimal.valueOf(d)`. Rejeter aussi `abs(scale) > 20` ou `precision > 20`.
  2. Contrôler la taille du tableau **dans** `convertir()` (ou `charge.size() > LOT_MAX` dans le contrôleur) avant toute conversion.
  3. Borner `vitesse` (0-400), `altitude` (-500 à 9000), `direction` et `kilometrage` (0 à 10^7) dans `PositionBrute`, avec la même logique que `coordonneesValides()`.
  4. En défense en profondeur, limiter la taille du corps sur `/api/public/gps/**` (par exemple 256 Ko via un filtre).

### E2. Jeton GPS unique et partagé par toute l'entreprise, sans lien avec le traceur : injection de fausses positions pour n'importe quel véhicule. Confirmé par le code.
- **Gravité** : Élevée. CVSS 3.1 indicatif 7.1 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:H/A:L). OWASP A07 Identification & Authentication Failures / A04, ASVS V2.10 (secrets de service), V11 (logique métier).
- **Emplacement** :
  - `gps/JetonGpsService.java:36-45` (un jeton par entreprise, sans expiration) et `:55-67` ;
  - `gps/ContexteGpsInterceptor.java:31-47` (le jeton désigne seulement l'entreprise) ;
  - `gps/position/PositionGpsService.java:76` (le véhicule est choisi par le seul `deviceId` déclaré dans la charge) ;
  - `:81-87` (horodatage rétroactif accepté jusqu'à 30 jours) ;
  - `:108-118` (compteur kilométrique) ;
  - `parc/camion/CamionDto.java:101` (`gpsDeviceId` renvoyé à tout profil).
- **Description** : le même secret sert à tous les boîtiers et passerelles de l'entreprise. Il n'est ni lié à un traceur, ni à une IP, ni à un véhicule. Il n'expire pas. L'interface recommande de le passer dans l'URL. Le serveur ne vérifie pas qu'un traceur n'émet que pour lui-même : le `deviceId` vient de la charge, et les IMEI sont lisibles par tout compte via la fiche véhicule. Conséquences pour qui obtient le jeton (chauffeur équipé d'un téléphone traceur ou d'un boîtier configurable, installateur, ancien prestataire) :
  - **réécrire les trajets** des 30 derniers jours de n'importe quel véhicule de l'entreprise (`RETARD_MAX = 30 j`) : faux alibi, arrêts masqués ;
  - **masquer un vol** : continuer d'émettre des positions plausibles pour un véhicule dont le vrai boîtier est coupé, ce qui empêche l'alerte `GPS_DECONNECTE` (`alerte/VeilleTraceurs.java:288-291`) ;
  - **créer de fausses alertes** (zone interdite, excès de vitesse, hors mission) ou en noyer de vraies ;
  - **gonfler le compteur kilométrique** : chaque position peut ajouter `SAUT_COMPTEUR_KM + heures × 150` = 200 km au minimum (heures ≥ 1, même pour deux points séparés d'une milliseconde, ligne 111). Avec 1000 positions par lot et `kilometrage` croissant, une requête ajoute jusqu'à 200 000 km, de façon irréversible (le compteur ne fait que monter). Cela fausse la maintenance par kilométrage et la rentabilité.
- **Scénario** : un chauffeur lit l'IMEI d'un collègue dans la fiche véhicule. Il récupère le jeton dans la configuration Traccar Client de son téléphone, puis envoie `POST /api/public/gps/positions/generique?jeton=gps_…` avec `{"deviceId":"<IMEI>","latitude":…,"longitude":…,"horodatage":"<hier>"}` pour réécrire la journée d'hier.
- **Correctif** :
  1. Un **jeton par traceur** (table `gps_traceur(device_id, empreinte_jeton, camion_id)`), ou au minimum vérifier que le `deviceId` reçu appartient à une liste blanche liée au jeton de la passerelle.
  2. Pour les boîtiers directs, passer à un jeton par véhicule et refuser une position dont le `deviceId` ne correspond pas à ce jeton.
  3. Refuser les positions **plus anciennes que la dernière connue** au-delà d'une petite fenêtre (par exemple 24 h), ou les marquer « différées » sans recalculer l'historique ni les alertes.
  4. Compteur : utiliser l'écart réel de temps (`Duration.toMinutes()`, sans `+1 h` forcé), plafonner l'augmentation par lot et journaliser tout saut.
  5. Ne renvoyer `gpsDeviceId` qu'aux profils ADMIN et GESTIONNAIRE_PARC.
  6. Ajouter une date d'expiration ou une rotation conseillée, un bouton « révoquer », et l'affichage de l'IP et du dernier usage.

---

## Moyenne

### M1. Le chauffeur voit toute la flotte en temps réel, les trajets de 31 jours de tout véhicule et toutes les alertes. Confirmé par le code.
Il n'y avait pas de compte chauffeur de test : l'accès n'a pas été vérifié par requête.
- **Gravité** : Moyenne. CVSS 4.3 (AV:N/AC:L/PR:L/UI:N/S:U/C:L/I:N/A:N). OWASP A01 Broken Access Control, ASVS V4.2. Données personnelles de localisation des collègues.
- **Emplacement** :
  - `gps/GpsController.java:76-80` (`/carte`), `:82-97` (`/camions/{id}/trajet`), `:99-106` (`/camions/{id}/dernieres`) : aucun `@PreAuthorize` ;
  - `gps/alerte/AlerteGpsController.java:31-48` ;
  - `gps/zone/ZoneGeofenceController.java:35-45` ;
  - côté front, `frontend/src/app/app.routes.ts:211-215` (route `gps` sans `roleGuard`) et `layout/shell.ts:176` (entrée « Carte du parc » sans `roles`).
- **Description** : le module GESTION_PARC admet le profil CHAUFFEUR (`ModuleApplicatif.java:36`). Le seul contrôle est donc l'authentification. Un chauffeur obtient :
  - la position en direct de tous les véhicules, avec le nom du chauffeur et la mission (`CarteParcService.java:54-64`) ;
  - l'historique détaillé de n'importe quel véhicule sur un mois ;
  - les excès de vitesse des autres ;
  - les zones (dépôts, zones interdites).

  Cela va à l'encontre du principe `security/Terrain` (« ses missions, son véhicule »).
- **Scénario** : `GET /api/gps/camions/{id d'un collègue}/trajet?debut=…&fin=…` avec un jeton de chauffeur permet de suivre les déplacements d'un collègue ou de repérer un camion chargé.
- **Correctif** : `@PreAuthorize(Autorisations.GESTION)` sur `/carte`, `/alertes/**` et `/zones/**`. Pour `/trajet` et `/dernieres`, autoriser `TERRAIN` puis, si `Terrain.chauffeurSeul()`, vérifier que le véhicule est celui de sa mission ou de son affectation en cours. Ajouter `roleGuard('ADMIN','GESTIONNAIRE_PARC')` sur la route `gps` et `roles` sur l'entrée de menu. Si le produit veut que le chauffeur voie la carte, ne lui montrer que son véhicule.

### M2. Le canal temps réel `/gps/positions` et `/gps/alertes` est ouvert à tout compte de l'entreprise, quel que soit son module ou son rôle. Confirmé par le code.
- **Gravité** : Moyenne. CVSS 4.3 (C:L). OWASP A01. C'est la variante GPS du constat « /incidents » de la maintenance, plus sensible ici puisqu'il s'agit de localisation.
- **Emplacement** :
  - `config/WebSocketConfig.java:61-64` : seul contrôle à l'abonnement, `DiffusionEntreprise.appartientA(destination, entreprise)` ;
  - `gps/position/PositionGpsService.java:170-175` : diffusion de `PositionGpsDto.Reponse` (immatriculation, coordonnées, vitesse, kilométrage, moteur) et des messages d'alerte.
- **Description** : l'abonnement STOMP ne contrôle ni le module (le `ControleModuleInterceptor` ne s'applique pas à `/ws`) ni le rôle. Un CAISSIER du PDV, un SERVEUR, un MAGASINIER, un ASSOCIÉ ou un CAISSIER_AGENCE d'une entreprise qui a aussi la gestion de parc peut s'abonner à `/topic/entreprises/{sonId}/gps/positions` et suivre la flotte en direct. Pour REST, ces profils sont pourtant refusés (403 « Votre profil n'a pas accès au module »). La session WebSocket survit en outre à la révocation du jeton (changement de mot de passe, `ver`) et à la suspension. Le cloisonnement entre entreprises tient : préfixe exact `"/topic/entreprises/" + id + "/"`, motifs Ant ancrés.
- **Remarque** : le frontend n'utilise pas du tout le WebSocket (aucune occurrence de `stomp`/`sockjs` dans `frontend/src`). Cette surface est donc inutile aujourd'hui.
- **Correctif** : soit retirer `/ws` et les `convertAndSend` tant que le front ne s'en sert pas, soit, dans le `ChannelInterceptor`, faire correspondre les sous-canaux à un module et à des rôles (`/gps/**` donne GESTION_PARC et ADMIN/GESTIONNAIRE_PARC) et revérifier `ver`/`actif` à chaque SUBSCRIBE. Fermer les sessions d'un compte à la révocation (`SimpUserRegistry` + `WebSocketSession.close`).

### M3. Volume non maîtrisé : 1,2 million de positions par minute et par jeton, aucune rétention, trajet sans plafond de points, quota partagé. Confirmé par le code.
Pas de test de charge, conformément aux consignes.
- **Gravité** : Moyenne. CVSS 5.3 (PR:L/A:H partiel). OWASP A04, ASVS V11.1.4 (anti-automatisation).
- **Emplacement** :
  - `gps/ContexteGpsInterceptor.java:45` : 1200 requêtes/min par **entreprise** ;
  - `position/PositionGpsService.java:39,59` : 1000 positions par requête ;
  - `:89-120` : pour chaque position, environ 6 à 10 requêtes SQL. Les zones actives sont rechargées pour **chaque** position (`SurveillanceGpsService.java:89`), avec en plus `exists…` (alertes), `missionEnCoursDuCamion`, la position précédente et une mise à jour du véhicule ;
  - `CarteParcService.java:79` et `PositionGpsRepository.java:30-37` : le trajet charge **tous** les points de 31 jours en mémoire puis les sérialise (`GpsController.java:92-95` borne la durée, pas le nombre) ;
  - aucune purge de `position_gps` dans le code ni dans les migrations.
- **Scénarios** :
  1. Avec un jeton (voir E2), on peut envoyer 1,2 M de positions par minute. La base grossit sans limite, les pools JDBC saturent, et le trajet d'un véhicule devient un `OutOfMemoryError` à la lecture : des millions de points, demandés par n'importe quel profil (voir M1).
  2. Le quota étant par entreprise, un tiers qui a le jeton l'épuise et **coupe la passerelle légitime** (429).
  3. Même sans attaque, un traceur à 10 s produit environ 268 000 points par mois, soit des dizaines de Mo de JSON pour un seul trajet.
- **Correctif** :
  - plafonner les positions par **traceur** et par minute (par exemple 12) et dédoublonner `(camion, horodatage)` avec un index unique ;
  - charger les zones une fois par lot ;
  - plafonner le trajet (par exemple 10 000 points), avec une simplification Douglas-Peucker ou un échantillonnage côté SQL (`row_number() % n`), ou paginer ;
  - ajouter une tâche de rétention (par exemple 12 mois) ou un partitionnement par mois ;
  - utiliser une clé de limiteur par jeton **et** par IP.

---

## Faible

### F1. Le jeton GPS est transmis dans l'URL, sur recommandation même de l'interface. Confirmé.
- **Gravité** : Faible. CVSS 3.1 (AV:N/AC:H/PR:N/C:L). OWASP A02/A09, ASVS V3.1.1 (pas de secret dans l'URL).
- **Emplacement** : `gps/GpsController.java:67` (`"/api/public/gps/positions/traccar?jeton=" + jeton`), `gps/ContexteGpsInterceptor.java:32`, `frontend/src/app/pages/carte-parc/carte-parc.html:194-202`.
- **Description** : les chaînes de requête finissent dans les journaux d'accès des proxys inverses et des CDN, ainsi que dans ceux de Traccar (forward.url). Le jeton n'expire pas : une fuite de journal donne un accès durable en écriture (E1, E2, M3). Les journaux locaux actuels n'en contiennent pas (`api-8085.log` : 0 occurrence de `jeton=`).
- **Correctif** : présenter en premier la configuration par en-tête (Traccar accepte `forward.header`, par exemple `X-Jeton-Gps: …`). Garder `?jeton=` en dernier recours, signalé comme moins sûr. Masquer `jeton=` dans le format des journaux du proxy. Donner une durée de vie au jeton (voir E2).

### F2. Génération et rotation du jeton GPS absentes du journal d'audit ; aucune révocation sans remplacement. Confirmé.
- **Gravité** : Faible. OWASP A09, ASVS V7.2.
- **Emplacement** : `gps/JetonGpsService.java:36-45`. Les écritures passent par `JdbcTemplate` et échappent donc à `common/audit/EcouteurAudit` (JPA).
- **Scénario** : un administrateur malveillant ou un compte ADMIN volé régénère le jeton. Toutes les passerelles légitimes sont coupées, ou bien il récupère un jeton pour injecter des positions (E2), sans aucune trace de qui l'a fait ni quand.
- **Correctif** : écrire une entrée dans `journal_activite` (auteur, IP, « jeton GPS régénéré », sans le jeton) ; ajouter `DELETE /api/gps/jeton` (révoquer).

### F3. Essais de jetons invalides non limités ; écriture en base à chaque envoi. Confirmé.
- **Gravité** : Faible. ASVS V2.2.1.
- **Emplacement** : `gps/ContexteGpsInterceptor.java:33-45`. Le limiteur n'est appliqué **qu'après** la reconnaissance du jeton. `JetonGpsService.java:65` fait un `UPDATE dernier_usage` à chaque requête valide.
- **Description** : la force brute est infaisable (192 bits aléatoires, empreinte SHA-256), mais chaque essai invalide coûte un `SELECT` sans aucun plafond par IP ni détection. Test local : `POST /api/public/gps/positions/generique?jeton=abc` et `X-Jeton-Gps: gps_00` renvoient tous deux `401 {"status":401,"message":"Jeton GPS inconnu"}`.
- **Correctif** : `limiteur.exiger("gps-ip:" + ip, 120, 1 min)` avant la recherche (après correction du X-Forwarded-For déjà signalée). Ne mettre à jour `dernier_usage` qu'une fois par minute au plus.

### F4. Lot entier rejeté par une seule valeur hors bornes, et injection dans les journaux via `deviceId`. Confirmé par le code.
- **Gravité** : Faible. OWASP A09 (injection de journaux, CWE-117), robustesse.
- **Emplacement** :
  - `position/PositionGpsService.java:100` : `altitude` non bornée, colonne `numeric(8,2)`. Une valeur ≥ 10^6 fait échouer l'INSERT, donc la transaction et le lot entier (500), contrairement à l'intention de la ligne 67 (« une position inutilisable est écartée seule ») ;
  - `AdaptateurGenerique.java:64-66` : un epoch négatif géant lève une `DateTimeException`, donc 500 ;
  - `PositionGpsService.java:69,73,78,85,116` : `log.warn(..., brute.deviceId())` avec un `deviceId` libre de 80 caractères et plus (la charge n'est pas tronquée avant le log). Des CR/LF permettent de forger des lignes de journal.
- **Correctif** : valider ou borner chaque champ dans l'adaptateur (voir E1) et écarter la position fautive seule. Assainir les valeurs journalisées (`replaceAll("[\\r\\n\\t]", "_")`, troncature à 80) ou encoder les journaux en JSON.

---

## Info (contrôlé, rien à corriger)

- **Cloisonnement entre entreprises : OK, confirmé par test.** ADMIN de l'entreprise 13 contre les données de l'entreprise 1 :
  - `GET /api/gps/camions/1/trajet` renvoie `404 "Vehicule introuvable (id=1)"` ;
  - `GET /api/gps/camions/1/dernieres?nombre=5` renvoie `200 []` ;
  - `GET /api/gps/zones/1` renvoie `404` ;
  - `/api/gps/carte` et `/api/gps/alertes` renvoient des listes vides.

  `PositionGps` porte `@TenantId` (`PositionGps.java:42-44`). Le `deviceId` est résolu dans l'entreprise du jeton (`findByGpsDeviceId` cloisonné, index unique `(entreprise_id, gps_device_id)` en V13) : un jeton ne peut pas écrire sur les véhicules d'une autre entreprise. `VeilleTraceurs` (SQL système) repose le contexte entreprise par entreprise.
- **XSS sur la carte : RAS.**
  - `carte-parc.ts:403-405` échappe `& < > " '` pour l'immatriculation, l'activité, le chauffeur et le nom de zone (lignes 207, 270-271, 279, 298) ;
  - le pictogramme passe par une liste blanche (`shared/picto-vehicule.ts:30-31`) ;
  - `zone.couleur` (texte libre de 20 caractères) n'est utilisée que dans les options `L.circle`, posées par `setAttribute` SVG ;
  - les messages d'alerte sont affichés par interpolation Angular.

  Leaflet ^1.9.4 n'a pas de CVE connue à ce jour.
- **SSRF : aucune.** Le serveur ne fait aucun appel sortant dans `gps/**` : pas de géocodage, et les tuiles OSM sont chargées par le navigateur. Remarque vie privée et conformité : les tuiles demandées révèlent à `tile.openstreetmap.org` les zones consultées, et la politique d'usage OSM déconseille l'usage commercial intensif. Prévoir un fournisseur de tuiles sous contrat ou auto-hébergé.
- **Période d'historique** : bornée à 31 jours (`GpsController.java:93-95`) ; `nombre` de `/dernieres` est borné à 500, celui de `/alertes/recentes` à 200. Le défaut restant est le nombre de points, traité en M3.
- **Jeton** : 24 octets `SecureRandom`, seule l'empreinte est stockée et le jeton n'est montré qu'une fois. Lui-même n'est pas devinable.

---

## Synthèse

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 2 (E1, E2) |
| Moyenne | 3 (M1, M2, M3) |
| Faible | 4 (F1 à F4) |
| Info | 5 |

**Trois priorités**
1. **E1** : valider les nombres des adaptateurs (pas de `new BigDecimal(texte)` libre, bornes, taille du lot contrôlée avant conversion). Une seule requête paralyse l'API de toutes les entreprises.
2. **E2 + F1** : passer à un jeton par traceur ou une liste blanche de `deviceId` par passerelle, refuser les réécritures rétroactives et les sauts de compteur, passer le jeton en en-tête et le faire expirer.
3. **M1 + M2** : réserver la carte, les trajets, les alertes et les zones à ADMIN/GESTIONNAIRE_PARC (le chauffeur limité à son véhicule) et fermer ou filtrer par rôle le canal STOMP `/gps/**`.

**État par surface (GPS)**
- **Authentification** (traceurs) : jeton robuste mais unique, partagé, permanent, souvent dans l'URL, non audité. À renforcer.
- **Autorisation et cloisonnement** : cloisonnement entre entreprises correct (testé). Contrôle des rôles insuffisant (chauffeur, profils hors module via STOMP).
- **Argent et caisses** : sans objet. Effet indirect : le compteur kilométrique falsifiable (E2) fausse la maintenance et la rentabilité.
- **Points publics** : `/api/public/gps/positions/{fournisseur}` est vulnérable au DoS (E1), à l'injection de fausses positions (E2) et au volume (M3).
- **Injection** : aucune SQL (JPQL paramétrée, `JdbcTemplate` à paramètres liés), aucune XSS. Injection de journaux mineure (F4).
- **Infra** : WebSocket `/ws` exposé mais inutilisé par le front. Pas de rétention des positions.
