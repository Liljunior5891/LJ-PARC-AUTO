# Audit sécurité 7/13 — Module MAINTENANCE (LJ SAAS, branche saas)

Date : 2026-11-11. Audit uniquement : aucun fichier du dépôt n'a été modifié, rien n'a été écrit dans la base, l'API 8085 n'a pas été relancée.

Périmètre : `backend/src/main/java/tg/parcauto/api/maintenance/` (entretien, incident, pneu, `EtatVehicule`) et l'écran Angular `frontend/src/app/pages/maintenance/` (+ `core/services/maintenance.service.ts`, `core/models/maintenance.ts`).
Remarque : le module ne contient ni garages, ni plans d'entretien séparés, ni pièces détachées en stock, ni upload, ni PDF, ni export CSV. Les « plans d'entretien » sont les périodicités portées par chaque intervention (`periodiciteKm`, `periodiciteJours`) et l'occurrence suivante créée à la réalisation.

Tests dynamiques (local, 8085, lectures GET seulement) : jetons forgés avec la clé JWT de développement (déjà signalée dans l'audit socle) pour un ADMIN d'entreprise et un CAISSIER. Aucune entreprise locale n'a à la fois le module Gestion de parc actif ET un profil hors gestion (chauffeur, caissier…) : le constat M1 est donc **confirmé par le code**, pas par une requête. Les tables `incident`, `entretien`, `pneu` sont vides en local.

---

## Synthèse

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 0 |
| Moyenne | 3 |
| Faible | 6 |
| Info | 3 |

Points solides constatés : cloisonnement `@TenantId` sur les trois entités (aucune requête native, aucun `JdbcTemplate`) ; `@Version` sur toutes les entités (les doubles réalisations, doubles `refaireDepense` ou doubles montages concurrents échouent en verrou optimiste) ; index unique `uk_pneu_position_montee` (deux pneus montés sur la même position impossible même en course) ; contraintes uniques par entreprise (V13) ; DTO en `record` sans `statut`/`entrepriseId` (pas de mass assignment) ; entretiens et pneus réservés à `GESTION` au niveau de la classe ; intervention close non modifiable ; incident résolu ne peut plus que se clore ; `LectureSeuleInterceptor` couvre toute écriture ; écran Angular sans `innerHTML` sur les données de maintenance (le `bypassSecurityTrustHtml` de `shared/dessin-vehicule.ts:293` ne prend que des constantes du code).

---

## MOYENNE

### M1 — Incidents lisibles par tout profil de l'entreprise (caissier, serveur, préparateur, magasinier, associé, chauffeur)
- Gravité : Moyenne — CVSS 3.1 indicatif 4.3 (AV:N/AC:L/PR:L/UI:N/S:U/C:L/I:N/A:N) — OWASP A01 Broken Access Control.
- Statut : **confirmé par le code** (pas de compte non-gestion dans une entreprise avec parc actif en local pour le rejouer).
- Emplacement :
  - `backend/src/main/java/tg/parcauto/api/maintenance/incident/IncidentController.java:26-51` : pas de `@PreAuthorize` au niveau de la classe ; `lister` (l.35-39), `ouverts` (l.41-45) et `detail` (l.47-51) n'en ont pas non plus. Seules les écritures sont protégées (`TERRAIN` l.55, `GESTION` l.63).
  - `SecurityConfig.java:56` : `anyRequest().authenticated()` seulement.
  - Diffusion temps réel : `IncidentService.java:251` envoie l'incident complet sur `/topic/entreprises/{id}/incidents` ; `config/WebSocketConfig.java:61-63` ne contrôle que l'entreprise, pas le rôle.
  - Le frontend, lui, réserve l'écran à `ADMIN`/`GESTIONNAIRE_PARC` (`app.routes.ts:218`) : l'intention n'est pas respectée côté serveur. Les entretiens et pneus (même module) sont bien en `GESTION` au niveau de la classe ; la mémoire de la revue du 2026-11-10 indique « lectures … atelier réservées GESTION » : les incidents ont été oubliés.
- Données exposées (`IncidentDto.Reponse`) : nom du chauffeur, mission, description libre (souvent nominative : accident, faute), lieu, latitude/longitude, photo, coût estimé, délai de résolution.
- Aggravant — oracle de tri : `GET /api/incidents?sort=chauffeur.salaireMensuel` est accepté (testé en ADMIN : `sort=chauffeur.telephone` → 200, tri appliqué). Un caissier peut donc classer les incidents par salaire, téléphone de garant, n° de permis… des chauffeurs (champs de `parc/chauffeur/Chauffeur.java:68-131`) sans les voir.
- Scénario : un caissier ou serveur d'une entreprise qui a les modules PDV + Parc appelle `GET /api/incidents?size=2000` avec son propre jeton et récupère tout l'historique des incidents, positions GPS comprises ; il s'abonne aussi au canal STOMP `/topic/entreprises/{id}/incidents` pour les recevoir en temps réel. Un chauffeur voit les incidents de tous ses collègues.
- Preuve (code) :
  ```java
  @RestController @ModuleRequis(ModuleApplicatif.GESTION_PARC) @RequestMapping("/api/incidents")
  public class IncidentController {
      @GetMapping public Page<IncidentDto.Reponse> lister(@PageableDefault(size = 20) Pageable pageable) {...}
      @GetMapping("/ouverts") public List<IncidentDto.Reponse> ouverts() {...}
      @GetMapping("/{id}") public IncidentDto.Reponse detail(@PathVariable Long id) {...}
  ```
- Correctif :
  1. Ajouter `@PreAuthorize(Autorisations.GESTION)` sur la classe `IncidentController` et garder `@PreAuthorize(Autorisations.TERRAIN)` sur `declarer` (l'annotation de méthode prime). Si le chauffeur doit revoir ses déclarations, ajouter `GET /api/incidents/miens` filtré par `Terrain.chauffeurId()`.
  2. Remplacer le `Pageable` libre par une liste blanche de tris (`survenuLe`, `statut`, `gravite`, `type`) : par exemple un `Pageable` reconstruit dans le contrôleur à partir d'un paramètre `tri` contrôlé, ou un `SortHandlerMethodArgumentResolverCustomizer` global qui rejette les propriétés imbriquées et inconnues (corrige aussi F5).
  3. Canal temps réel : soit vérifier le rôle à l'abonnement pour les sous-canaux sensibles (`/incidents`, positions GPS) dans `WebSocketConfig.preSend`, soit diffuser un résumé sans description/GPS et laisser le client relire l'API protégée.

### M2 — Déni de service CPU par prix d'achat géant sur la fiche pneu (`1E+30000000`)
- Gravité : Moyenne — CVSS 4.9 (AV:N/AC:L/PR:H/UI:N/S:U/C:N/I:N/A:H) — OWASP A04 Insecure Design / API4 Unrestricted Resource Consumption.
- Statut : **confirmé par le code + mesure isolée** (pas lancé contre l'API, par consigne de non-DoS).
- Emplacement :
  - `maintenance/pneu/PneuDto.java:23` : `@PositiveOrZero BigDecimal prixAchat` (aucun `@Digits`/`@DecimalMax`).
  - `maintenance/pneu/PneuService.java:62-66` (`modifier`) → `appliquer` l.149 `p.setPrixAchat(...)` → `PneuDto.Reponse.de(repository.save(pneu))` : l'entité est gérée, `save` = `merge` sans flush ; la réponse est calculée **avant** que PostgreSQL ne refuse la valeur.
  - `maintenance/pneu/Pneu.java:133-138` : `prixAchat.divide(BigDecimal.valueOf(parcourus), 2, RoundingMode.HALF_UP)` — avec un exposant énorme, la division à l'échelle 2 matérialise toutes les décimales.
- Condition : pneu monté (ou déposé) avec des kilomètres parcourus > 0 (cas normal).
- Preuve (mesure dans une JVM séparée, Java 21) :
  ```
  1E+1000000   divide scale 2 :   119 ms
  1E+4000000   divide scale 2 :   536 ms
  1E+30000000  divide scale 2 : 12 673 ms   (≈ 30 millions de chiffres)
  1E+999999999 -> ArithmeticException immédiate (borne de BigInteger)
  ```
  La valeur malveillante tient en 11 caractères, donc passe la limite `maxNumberLength` de Jackson et `@PositiveOrZero` (simple `signum`). Chaque `PUT /api/pneus/{id}` avec `{"reference":"X","prixAchat":1E+30000000}` occupe un thread Tomcat ~13 s (plus avec `1E+100000000`) avant l'échec SQL et le rollback ; quelques requêtes parallèles saturent les cœurs.
- Même famille que le constat finance déjà remonté, mais ici à un emplacement propre à la maintenance.
- Correctif :
  - Borner tous les montants du module à la colonne : `@Digits(integer = 14, fraction = 2) @DecimalMax("99999999999999.99")` sur `PneuDto.Requete.prixAchat`, `EntretienDto.Realisation.cout`, `IncidentDto.Requete.coutEstime`, `IncidentDto.Traitement.coutEstime`.
  - Mieux, en défense globale : un `Jackson2ObjectMapperBuilderCustomizer` qui enregistre un désérialiseur `BigDecimal` refusant `scale() < -20` ou `precision() > 30` (protège tous les modules d'un coup).
  - Dans `Pneu.coutParKm()`, ne pas diviser si `prixAchat.precision() - prixAchat.scale() > 16`.

### M3 — Compteur du véhicule gonflable sans retour via la réalisation d'un entretien
- Gravité : Moyenne — CVSS 4.3 (AV:N/AC:L/PR:H/UI:N/S:U/C:N/I:L/A:L) — OWASP A04.
- Statut : **confirmé par le code** (réapparition à un emplacement propre de la maintenance du constat « compteur gonflable » de l'exploitation).
- Emplacement : `maintenance/entretien/EntretienDto.java:33` (`@NotNull @PositiveOrZero Long kilometrageRealise`, sans borne) ; `maintenance/entretien/EntretienService.java:157-158` (`camion.setKilometrageActuel(realisation.kilometrageRealise())` si supérieur) ; la dépense générée relaie aussi ce relevé (`EntretienService` `enregistrerDepense` → `DepenseService.mettreAJourCompteurCamion`, `finance/depense/DepenseService.java:398-405`).
- Scénario : un gestionnaire (ou une faute de frappe : 1 200 000 au lieu de 120 000) réalise une vidange avec `kilometrageRealise = 9223372036854775000`. Le compteur du véhicule est porté à cette valeur. Comme la fiche véhicule « ne recule plus le compteur » (correctif c75af78), l'erreur est **irréversible par l'interface**. Ensuite : toutes les échéances kilométriques passent en retard, `Entretien.prochainKilometrage()` (`Entretien.java:146`, `kilometrageRealise + periodiciteKm`) déborde en négatif, la consommation et l'usure des pneus (`Pneu.kilometresParcourus`) deviennent absurdes, et le compteur GPS « plausible » se cale sur une base fausse.
- Correctif : borner le relevé (`@Max(5_000_000)`) et refuser un saut supérieur à un seuil (par ex. 50 000 km ou un plafond `km/jour × jours écoulés` depuis le dernier relevé) sauf confirmation explicite par un ADMIN ; centraliser la mise à jour du compteur dans un seul service (`CamionService.releverCompteur(camion, km, source)`) appelé par missions, dépenses, GPS et entretiens, avec la même règle et une trace d'audit.

---

## FAIBLE

### F1 — Le chauffeur peut rattacher à son incident une mission qui n'est pas la sienne (ni celle du véhicule)
- OWASP A01 (IDOR intra-entreprise). Confirmé par le code.
- Emplacement : `maintenance/incident/IncidentService.java:73-86` vérifie bien que le **véhicule** est confié au chauffeur seul, puis l.90-91 : `incident.setMission(requete.missionId() == null ? null : missionService.charger(requete.missionId()))` sans contrôle ; `MissionService.charger` (`exploitation/mission/MissionService.java:207`) ne vérifie ni `Terrain`, ni le véhicule.
- Scénario : un chauffeur déclare un incident sur son camion avec `missionId` d'une mission d'un collègue ; la réponse renvoie `missionNumero` (énumération des numéros de missions par id) et l'incident apparaît rattaché à la mission d'un autre (imputation erronée, éventuellement pour se décharger). Même incohérence possible côté gestionnaire (mission sur un autre véhicule).
- Correctif : si `missionId` est fourni, exiger `mission.getCamion().getId().equals(camion.getId())` et, pour le chauffeur seul, `Terrain.exigerLeSien(mission.getChauffeur().getId(), "La mission")`. Tant qu'à faire : `@Size(max = 4000)` sur `description`, `survenuLe` refusé s'il est dans le futur (`IncidentService.java:99`, sinon `delaiResolutionHeures` négatif).

### F2 — Montants sans borne de précision : 500 et arrondis silencieux
- OWASP A04. Confirmé par le code.
- Emplacement : `EntretienDto.java:34` (`cout`), `IncidentDto.java:31` et `:39` (`coutEstime`), `PneuDto.java:23` (`prixAchat`) ; colonnes `numeric(16,2)` (`Entretien.java` `cout`, `Incident.java:89`, `Pneu` `prix_achat`).
- Effets : un montant à plus de 14 chiffres entiers ou `1E+999999999` → erreur SQL → 500 « Erreur technique » (pas de DoS sur ces chemins car aucun calcul avant l'insertion, sauf M2) ; un montant à 3 décimales (`100.005`) est arrondi en base sans que l'utilisateur le sache, et la dépense générée (`EntretienService` `enregistrerDepense`) reçoit la valeur non arrondie.
- Correctif : `@Digits(integer = 14, fraction = 2)` sur ces champs (même correctif que M2).

### F3 — Périodicités sans borne : clôture d'entretien bloquée et échéances faussées
- OWASP A04. Confirmé par le code.
- Emplacement : `EntretienDto.java:23-24` (`@Positive Long periodiciteKm`, `@Positive Integer periodiciteJours`) ; `Entretien.java:146` et `:154`.
- Effets : `periodiciteJours = 2147483647` → `dateRealisation.plusDays(...)` donne l'an ~5 880 000, au-delà de la borne du type `date` PostgreSQL (5874897) → l'occurrence suivante ne s'enregistre pas et **toute la réalisation est annulée (500)** tant qu'on ne repasse pas par `modifier` ; `periodiciteKm = Long.MAX_VALUE` → débordement → `kilometragePrevu` négatif → alerte « en retard » permanente.
- Correctif : `@Max(1_000_000)` sur `periodiciteKm`, `@Max(3650)` sur `periodiciteJours`, `@Max(5_000_000)` sur `kilometragePrevu` ; `Math.addExact` dans `prochainKilometrage()`.

### F4 — Fiche pneu modifiable après génération de la dépense et après mise au rebut
- OWASP A04 (modification après validation). Confirmé par le code.
- Emplacement : `maintenance/pneu/PneuService.java:62-66` et `:144-155` : aucun contrôle de statut ni de `pneu.getDepense()` ; `prixAchat`, `dateAchat`, `dureeViePrevueKm`, `etat` restent modifiables, même pour un pneu `REBUTE`.
- Scénario : un pneu acheté 180 000 F est monté (dépense `ACHAT_PNEU` de 180 000 F générée, `PneuService.java:94-103`, éventuellement validée par l'admin) ; on modifie ensuite la fiche à 18 000 F : le coût au km affiché pour la marque est divisé par dix alors que la dépense reste à 180 000 F (ou l'inverse pour gonfler un comparatif). Aucune trace de l'ancien prix dans le module.
- Correctif : figer `prixAchat`/`dateAchat` dès que `depense != null` (ou n'accepter la modification qu'en passant par la correction de la dépense, qui suit déjà le circuit de validation) ; refuser toute modification d'un pneu `REBUTE`.

### F5 — Tri sur propriété inconnue → 500, et tri sur champs internes accepté (`/api/incidents`)
- OWASP A05/A04. **Confirmé dynamiquement** (ADMIN entreprise 6, local) :
  ```
  GET /api/incidents?sort=inexistant          -> 500 {"message":"Erreur technique du serveur"}
  GET /api/incidents?sort=chauffeur.telephone -> 200 (tri appliqué)
  GET /api/incidents?size=100000              -> 200, pageSize ramené à 2000
  ```
- Emplacement : `IncidentController.java:37` (`Pageable` libre) + `IncidentRepository.java` `tous(Pageable)`.
- Réapparition propre à la maintenance du constat « tri sur champs internes → 500 » du parc ; aggrave M1 (oracle).
- Correctif : liste blanche de tris (voir M1) et transformation de `PropertyReferenceException` en 400 dans le gestionnaire d'erreurs global.

### F6 — Kilométrages de montage/dépose des pneus sans borne : usure et coût au km négatifs
- OWASP A04. Confirmé par le code.
- Emplacement : `PneuDto.java:34` et `:40` ; `PneuService.java:79-86` (`kilometresAnterieurs + Math.max(0L, depose - montage)`) ; `Pneu.java` `kilometresParcourus()`.
- Effet : une dépose à `Long.MAX_VALUE` puis un remontage et une nouvelle dépose font déborder `kilometresAnterieurs` en négatif → usure, alerte de remplacement et `coutParKm` négatifs, comparatif des marques faussé. Le kilométrage de montage peut aussi être très supérieur au compteur réel du véhicule.
- Correctif : `@Max(5_000_000)` sur ces relevés, `Math.addExact`, et refuser un montage au-delà du compteur courant du véhicule + marge.

---

## INFO

### I1 — Transitions d'incident libres
`IncidentService.java:128` applique n'importe quel `statut` reçu (sauf depuis RESOLU/CLOS) : passage à `EN_REPARATION` sans intervention, retour de `EN_REPARATION` à `DECLARE` alors qu'une intervention est ouverte, `coutEstime` modifiable jusqu'à la clôture (l.132-133). Pas d'impact de sécurité direct ; conseiller une table de transitions autorisées et d'exiger un `resolution` non vide pour RESOLU/CLOS. De même, `EntretienService.annuler` écrase `observations` par le motif sans le conserver à part et accepte un motif vide.

### I2 — Coût d'entretien réintroductible après annulation de sa dépense
`POST /api/entretiens/{id}/depense` (`EntretienService.refaireDepense`) recrée la dépense si elle est `REFUSEE` ou `ANNULEE`. Avec la validation activée, la nouvelle dépense repart en `ATTENTE_ADMIN` (pas de contournement) ; sans validation, un gestionnaire peut réintroduire immédiatement un coût que l'administrateur vient d'annuler, et le coût de l'intervention réalisée n'est plus corrigeable. Comportement voulu d'après la mémoire du projet ; à documenter, ou réserver la réintroduction après `ANNULEE` à l'ADMIN.

### I3 — Listes non paginées
`GET /api/pneus` (`PneuService.lister` → `findAll()`), `GET /api/entretiens`, `/api/entretiens/alertes`, `/api/incidents/ouverts` renvoient tout en une fois (les alertes filtrent en mémoire). Volume borné par entreprise, mais à passer au chargement par tranches (composant `SuiteListe`) pour les grandes flottes.

---

## Vérifications sans constat

- **IDOR entre entreprises** : `findById` et toutes les requêtes JPQL du module passent par le filtre `@TenantId` ; `GET /api/entretiens/camions/{id}` d'un véhicule étranger → `[]`, `GET /api/pneus/{id}` étranger → 404 (testé en local). `camionService.charger`, `chauffeurService.charger`, `missionService.charger` également cloisonnés.
- **Rôles** : entretiens et pneus : `GESTION` au niveau de la classe (le chauffeur et le caissier sont refusés) ; déclaration d'incident `TERRAIN` avec contrôle du véhicule confié (`Terrain.chauffeurSeul`) et chauffeur forcé à soi-même.
- **Conditions de course** : `@Version` (`EntiteAuditee.java:52`) sur entretien, incident, pneu ; index unique partiel sur la position montée ; contraintes uniques par entreprise pour `numero`/`reference`.
- **Modification après validation** : intervention `REALISE`/`ANNULE` non modifiable ; changement de véhicule refusé en atelier ; incident clos figé.
- **Mass assignment** : DTO `record` explicites, aucun champ `statut`, `depense`, `entrepriseId`, `version` exposé en écriture.
- **Injection** : aucune requête native ni concaténation dans le module.
- **Uploads / PDF / CSV** : aucun dans ce module (`photoUrl` d'incident est une simple chaîne de 500 caractères, non affichée par l'écran Angular de maintenance ; si elle l'est un jour, n'accepter que des chemins `/api/documents/...` ou `https:`).
- **XSS front** : `pages/maintenance/maintenance.html` n'utilise que l'interpolation Angular.
- **Lecture seule après expiration** : `LectureSeuleInterceptor` bloque toutes les méthodes autres que GET/HEAD/OPTIONS.

---

## Priorités

1. **M1** : `@PreAuthorize(Autorisations.GESTION)` sur `IncidentController` (+ endpoint « mes incidents » pour le chauffeur), filtrer le canal STOMP `/incidents`.
2. **M2 + F2** : `@Digits(integer = 14, fraction = 2)` sur tous les montants du module, et un désérialiseur `BigDecimal` global qui refuse les exposants extrêmes.
3. **M3 + F3 + F6** : bornes sur kilométrages et périodicités, et règle unique de relevé du compteur avec plafond de saut.

## État des surfaces (module maintenance)

- Authentification : rien de propre au module (socle déjà audité).
- Autorisation et cloisonnement : cloisonnement entre entreprises solide ; **lecture des incidents ouverte à tous les profils (M1)** ; mission d'incident non contrôlée (F1).
- Argent : montants recalculés nulle part (saisis), sans borne (M2 DoS, F2) ; dépenses générées via `DepenseService` donc soumises à la validation ; fiche pneu modifiable après dépense (F4).
- Points publics : aucun dans ce module.
- Injection : aucune.
- Infra : sans objet pour ce module.
