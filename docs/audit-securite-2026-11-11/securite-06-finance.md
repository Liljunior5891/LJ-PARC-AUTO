# Audit sécurité 6/13 — FINANCE (LJ SAAS, branche saas)

Périmètre : `backend/src/main/java/tg/parcauto/api/finance/**` (facture, recette, depense, rentabilite, commun) et écrans Angular `pages/factures`, `recettes`, `depenses`, `fournisseurs-parc`, `carburant`, `rentabilite`, `rapports`.
Le package ne contient **ni comptes bancaires, ni trésorerie, ni caisse du parc** : il n'y a donc rien à auditer sur ces surfaces côté parc.

Méthode : revue statique complète des 7 contrôleurs et services. Tests dynamiques **en lecture seule** sur `http://localhost:8085`. Les jetons de test ont été signés localement avec la clé JWT de développement, déjà signalée au socle. Aucune donnée n'a été écrite.

Le test d'acceptation d'un exposant géant envoyé à l'API a été **refusé par le garde-fou d'exécution**. Il a été remplacé par une preuve hors ligne : Jackson 2.18.2 et un banc `BigDecimal` exécutés dans le scratchpad.

Hors sujet, car déjà audités : clé JWT, `/api/auth/login`, localStorage, Swagger, double règlement fournisseur, justificatifs et dépenses modifiables, tri sur champs internes → 500, contrat modifiable après facturation.

---

## Synthèse

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 0 |
| Moyenne | 3 |
| Faible | 6 |
| Info | 4 |

**Les 3 priorités :**
1. Borner les montants : `@Digits` + `@DecimalMax`, ou un désérialiseur `BigDecimal` global. Cela ferme le déni de service par exposant (M1).
2. Borner la période de `/api/rentabilite/**` et supprimer la boucle jour par jour (M2).
3. Centraliser la règle « une seule facture active par mission ou contrat », avec un index unique partiel (M3).

**État des surfaces :**
- **Authentification** : sans objet propre au module. Tous les contrôleurs exigent un jeton (401 sans jeton, vérifié).
- **Autorisation et cloisonnement** : bon.
  - Les 7 contrôleurs portent `@PreAuthorize(GESTION)` au niveau de la classe. Validation, refus et réglages sont réservés à `ADMIN`.
  - Le front est protégé par `roleGuard('ADMIN','GESTIONNAIRE_PARC')`.
  - Accès d'une autre entreprise : 404 confirmé sur toutes les ressources testées.
  - Aucune requête native ni JDBC dans `finance/`.
- **Argent** : les totaux sont recalculés côté serveur et la TVA vient du paramétrage. Un paiement supérieur au reste est refusé, une facture réglée ne s'annule pas, et un mois de récurrente ne se paie qu'une fois (index unique). Il reste trois faiblesses :
  - double facturation possible (M3) ;
  - recettes effaçables sans motif (F1) ;
  - correction de dépense approuvée sans nouveau contrôle (F2).
- **Points publics** : aucun dans ce module.
- **Injection** :
  - JPQL et Criteria paramétrés ;
  - CSV du parc protégé contre les formules (`RapportService.echapper`) ;
  - PDF en OpenPDF, sans HTML ;
  - noms de fichiers générés par le serveur ;
  - aucun `innerHTML` ni `bypassSecurityTrust` sur les écrans finance (le seul `bypassSecurityTrustResourceUrl` de `rapports.ts:154` porte sur une URL `blob:` créée localement).
- **Disponibilité** : deux dénis de service applicatifs accessibles à un compte gestionnaire (M1, M2). Ils touchent toutes les entreprises, car la JVM est partagée.

---

## MOYENNE

### M1 — Déni de service par montant à exposant géant (`BigDecimal.setScale`) — confirmé hors ligne, non rejoué sur l'API
- **CVSS 3.1 indicatif** : 6.5 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:N/A:H) — OWASP A04 (conception non sûre) / ASVS 5.1.4, 13.1.
- **Emplacements** (montant reçu en JSON, sans borne, puis mis à l'échelle) :
  - `finance/facture/FactureLigne.java:418` : `quantite.multiply(prixUnitaire).setScale(2, …)`, appelé par `Facture.recalculer()` depuis `FactureService.appliquer:394`. Points d'entrée : `POST /api/factures` et `PUT /api/factures/{id}` (brouillon).
  - `finance/depense/ChargesParcService.java:207` : `r.montant().setScale(2, …)`, exécuté **avant toute écriture**. Points d'entrée : `POST` et `PUT /api/parc/charges/recurrentes`.
  - `finance/depense/FournisseurParcService.java:150` : `regler`.
  - `finance/depense/DepenseService.java:165` : acompte.
  - Les DTO ne portent que `@Positive` / `@PositiveOrZero` (`FactureDto.java:26-28`, `DepenseDto.java:31,49`, `ChargesParcService.java:68`, `FournisseurParcService.java:55`).
- **Description** : Jackson accepte `1E+999999999` comme `BigDecimal`. Le littéral fait 12 caractères, donc la limite `maxNumberLength` ne joue pas. `@Positive` ne lit que le signe. `setScale(2)` doit alors construire un entier de 10^1 000 000 001, soit environ 415 Mo, avec un coût CPU de plusieurs minutes. Le fil HTTP est bloqué et la JVM risque l'OOM. Toutes les entreprises de l'instance sont touchées.
- **Scénario** : un gestionnaire, ou un jeton volé, envoie quelques requêtes `PUT /api/parc/charges/recurrentes/{id}` avec `"montant": 1E+999999999`. L'API devient indisponible pour tous les clients.
- **Preuve** (exécutée hors API, dans le scratchpad) :
  ```
  Jackson 2.18.2 : readValue("{\"montant\":1E+999999999}") -> accepte, echelle=-999999999
  new BigDecimal("1E+1000000").setScale(2)   ->   144 ms
  new BigDecimal("1E+10000000").setScale(2)  ->  2041 ms
  new BigDecimal("1E+50000000").setScale(2)  -> 18649 ms   (croissance sur-linéaire)
  ```
  L'envoi réel à l'API (sur un identifiant inexistant) a été bloqué par le garde-fou ; il reste **à confirmer** par le propriétaire s'il le souhaite.
- **Correctif** :
  - Sur tous les montants et quantités des DTO : `@Digits(integer = 14, fraction = 2)` et `@DecimalMax("99999999999999.99")`. `DigitsValidator` calcule `precision - scale` sans matérialiser le nombre.
  - En défense globale, ajouter un module Jackson avec un `JsonDeserializer<BigDecimal>` qui refuse `scale() < -2 || scale() > 6 || precision() > 20`. Un `Jackson2ObjectMapperBuilderCustomizer` couvre tous les modules d'un coup (PDV, agences…).

### M2 — Déni de service par période illimitée sur la rentabilité — confirmé par le code
- **CVSS** : 6.5 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:N/A:H) — OWASP A04 / ASVS 13.1 (limites de ressources).
- **Emplacements** :
  - `finance/rentabilite/RentabiliteController.java:28-45` : `debut` et `fin` libres, sans `exigerPeriode`.
  - `finance/rentabilite/RentabiliteService.java:147-170` : boucles `while (!courant.isAfter(finContrat)) { joursExploites.add(courant); courant = courant.plusDays(1); }` sur un `HashSet<LocalDate>`.
- **Description** :
  - Pour un contrat **sans terme**, `finContrat` vaut la `fin` demandée (ligne 153). Un appel `GET /api/rentabilite/camions/{id}?debut=2000-01-01&fin=290000-12-31` fait donc environ 10^8 itérations et stocke environ 10^8 `LocalDate` en mémoire, soit plusieurs Go : OOM.
  - Un contrat de location sans date de fin est un cas métier normal.
  - Le pilotage applique `RapportService.exigerPeriode` (5 ans au plus), mais le contrôleur de rentabilité, lui, ne l'applique pas.
  - `/api/rentabilite/global` n'a pas de borne non plus, mais sans boucle jour par jour.
- **Scénario** : un gestionnaire, ou un jeton volé, appelle la fiche d'un véhicule en location ouverte avec une date de fin lointaine. Le tas de la JVM est saturé et toutes les entreprises sont touchées.
- **Correctif** :
  - Appeler `RapportService.exigerPeriode(debutRetenu, finRetenue)` dans les deux méthodes du contrôleur.
  - Dans `utilisation()`, remplacer l'ensemble de jours par une fusion d'intervalles triés, en O(n log n) sur le nombre de contrats et de missions et non plus sur le nombre de jours.
  - Borner `finContrat` à `min(fin, aujourd'hui)` pour un contrat ouvert.

### M3 — Double facturation d'une même mission ou d'un même contrat (contournement de la garde « pas deux factures ») — confirmé par le code
- **CVSS** : 4.9 (AV:N/AC:L/PR:H/UI:N/S:U/C:N/I:H/A:N) — OWASP A04 (logique métier) / ASVS 11.1.
- **Emplacements** :
  - La garde n'existe que dans `FactureService.genererDepuisContrat:98` et `genererDepuisMission:126`.
  - **Contournement 1** : `FactureService.creer:74` et `modifier:81`, via `appliquer:375-376`. `locationId` et `missionId` sont acceptés sans vérifier :
    - `existsBy…AndStatutNot(ANNULEE)` ;
    - que la mission est terminée ;
    - que le client de la facture est celui de la mission ou du contrat.
  - **Contournement 2** : `RecetteService.creer:93-99`, puis `facturer:109`, puis `FactureService.facturerRecette:194-235`, qui ne fait **aucun** contrôle d'existence. En plus, `DisponibiliteVehiculeService.occupations:66-85` **exclut** la mission et le contrat passés en paramètre. Une deuxième recette sur la même mission ne déclenche donc aucun conflit.
- **Description et impact** :
  - **Contournement 1** : à l'émission (`emettre`, puis `enregistrerRecettes:325-355`), les recettes existantes sont déjà rattachées à la première facture. `existantes` est donc vide et de **nouvelles recettes** sont créées par ligne. Le chiffre d'affaires et la rentabilité du véhicule sont comptés deux fois, et le client reçoit deux factures pour la même prestation.
  - **Contournement 2** : chaque `POST /api/recettes` avec `missionId` et `clientId` émet une facture supplémentaire. Elle est même marquée réglée si `modePaiement` est rempli.
- **Scénario** : un gestionnaire refacture une mission déjà facturée et payée, par l'écran Facturation (saisie libre) ou par une recette. La créance apparaît deux fois au client, ou les résultats du véhicule sont gonflés, par exemple pour une prime.
- **Correctif** :
  - Créer une méthode `exigerNonFacturee(location, mission)` et l'appeler dans `appliquer`, `facturerRecette` et les deux générateurs. Elle doit aussi vérifier `client == mission.client` ou `client == location.client`, et pour une mission le statut `TERMINEE`.
  - Contrainte en base :
    ```sql
    CREATE UNIQUE INDEX uk_facture_mission_active  ON facture (entreprise_id, mission_id)  WHERE mission_id  IS NOT NULL AND statut <> 'ANNULEE';
    CREATE UNIQUE INDEX uk_facture_location_active ON facture (entreprise_id, location_id) WHERE location_id IS NOT NULL AND statut <> 'ANNULEE';
    ```
  - Dans `RecetteService.creer`, refuser une recette sur une mission ou un contrat qui a déjà une recette non annulée (ou proposer de la modifier).

---

## FAIBLE

### F1 — Recettes, y compris en espèces, supprimables et modifiables par le gestionnaire sans motif ni validation — confirmé par le code
- **Gravité et catégorie** : Faible, CVSS 3.8 (PR:H/I:L), OWASP A04 / non-répudiation (ASVS 7.2).
- **Emplacements** :
  - `RecetteController.java:126-132` (`DELETE /api/recettes/{id}`) ;
  - `RecetteService.supprimer:188-195` (suppression physique) ;
  - `RecetteService.modifier:172-186`.
- **Description** :
  - Une recette **sans client** (recette d'appoint, souvent encaissée en espèces) n'est jamais facturée. Elle peut donc être supprimée physiquement, ou son montant baissé, par un gestionnaire, sans motif ni approbation.
  - Après l'annulation d'une facture, la recette détachée (`FactureService.annuler:284`) devient elle aussi supprimable.
  - Les dépenses suivent la règle inverse : annulation avec motif, validation par l'admin (`DepenseService`, commentaire ligne 36).
  - Seule trace : le journal d'audit (`EcouteurAudit.onPostDelete`).
- **Scénario** : le gestionnaire encaisse 150 000 F en espèces, saisit la recette puis la supprime ou la ramène à 50 000 F. La différence disparaît des résultats.
- **Correctif** :
  - Remplacer la suppression par une annulation avec motif : statut `ANNULEE`, auteur et date.
  - Soumettre la modification du montant d'une recette encaissée, et son annulation, à la validation de l'admin (même réglage `ReglageParc.validationDepenses`, ou un réglage dédié).

### F2 — Correction de dépense approuvée sans nouveau contrôle des règlements ni du statut — confirmé par le code
- **Gravité et catégorie** : Faible, OWASP A04.
- **Emplacement** : `DepenseService.valider:238-255`.
- **Description** : la correction stockée (`correctionEnAttente`) est appliquée par `appliquer()` sans refaire les contrôles de `modifier:198-205` :
  - nouveau montant au moins égal aux règlements ;
  - `aPayer=false` interdit s'il existe des règlements.

  Elle utilise `charger` et non `chargerActive`. Une correction en attente s'applique donc aussi à une dépense **annulée** entre-temps. Entre la soumission et l'approbation, des règlements peuvent être ajoutés (`regler` lit le montant courant), et la dette se retrouve en trop-payé ou des règlements deviennent orphelins.
- **Correctif** : après `appliquer`, rejouer les contrôles de `modifier` (règlements et `aPayer`), refuser si `statut != VALIDEE`, et effacer `correctionEnAttente` à l'annulation.

### F3 — Lien recette ↔ facture établi par la référence saisie : l'annulation peut garder ou supprimer la mauvaise recette — confirmé par le code
- **Gravité et catégorie** : Faible, OWASP A04 (intégrité).
- **Emplacements** :
  - `FactureService.annuler:280-286` : `facture.getNumero().equals(recette.getReference())` décide entre suppression et détachement.
  - `RecetteService.modifier:215` : `reference` reste modifiable même quand la recette est facturée (les lignes 175-182 ne bloquent que montant, véhicule et client).
  - `facturerRecette:205` : la recette garde la référence saisie par l'utilisateur.
- **Scénario** :
  - En renommant la référence d'une recette née de l'émission, l'annulation de la facture la **détache** au lieu de la supprimer : un revenu fictif reste compté, puis il peut être refacturé.
  - À l'inverse, une recette saisie avec la référence `FAC-AAAA-NNNN` (numéro prévisible) est supprimée physiquement à l'annulation.
- **Correctif** : ajouter une colonne `origine` (`EMISSION` / `SAISIE`) posée par `enregistrerRecettes`, et décider sur elle. Verrouiller `reference` et `dateRecette` quand une facture active est rattachée.

### F4 — Annulation d'une facture émise sans motif obligatoire ni approbation — confirmé par le code
- **Gravité et catégorie** : Faible, OWASP A04 / ASVS 7.2.
- **Emplacements** : `FactureController.java:137-142` (`@RequestParam(required = false) String motif`), `FactureService.annuler:266-288`.
- **Description** : un gestionnaire annule une facture émise non réglée, ce qui supprime les recettes nées de l'émission, sans motif. Le motif, s'il est fourni, **écrase** `observations` au lieu d'être conservé à part.
- **Correctif** : motif obligatoire (`@NotBlank`, dans le corps), champs `annuleePar`, `annuleeLe` et `motifAnnulation` dédiés, et validation par l'admin quand le réglage de validation est actif.

### F5 — Dates libres : émission antidatée, paiement daté dans le futur — confirmé par le code
- **Gravité et catégorie** : Faible, OWASP A04.
- **Emplacements** : `FactureDto.java:181-182` (`dateEmission`, `dateEcheance` sans borne), `FactureDto.java:205` (`datePaiement` sans `@PastOrPresent`), `FactureService.appliquer:377-378`, `enregistrerPaiement:255`.
- **Description** :
  - Une facture peut être émise à une date antérieure à la précédente : la numérotation n'est plus chronologique, ce qui pose un problème d'obligation fiscale. Elle peut aussi avoir une échéance antérieure à l'émission.
  - Un règlement peut être daté dans l'avenir.
  - Les encaissements du tableau de bord (`TableauBordService` → `PaiementRepository`) et les créances sont faussés.

  À noter : les recettes (`RecetteService:203`) et les règlements fournisseurs (`FournisseurParcService:155`) refusent déjà le futur.
- **Correctif** : `@PastOrPresent` sur `datePaiement`, `dateEcheance >= dateEmission`, et une date d'émission au moins égale à celle de la dernière facture émise, vérifiée à l'émission.

### F6 — Conclusion d'une demande de location en ligne sans contrôle du véhicule ni du client — confirmé par le code
- **Gravité et catégorie** : Faible, OWASP A04.
- **Emplacement** : `marketplace/DemandeLocationService.java:145-156`. L'événement transporte `camionId` (`RecetteService.conclureDemande:88`), mais l'écouteur ne le compare pas à `demande.getCamion()`, et le client n'est pas comparé non plus.
- **Description** : une recette sur un autre véhicule ou un autre client peut « conclure » n'importe quelle demande acceptée de l'entreprise. Le véhicule réservé reste alors bloqué ou libéré à tort, et la recette et sa facture passent au canal « en ligne ».
- **Correctif** : dans l'écouteur, exiger `demande.camion.id == evenement.camionId()` et que le client de la recette soit celui de la demande.

---

## INFO

- **I1 — Encaissement concurrent sur une facture (à vérifier)**. `FactureService.enregistrerPaiement:238-264` lit `resteAPayer()` sans verrou explicite.
  - La protection repose sur `@Version` de `Facture` (`EntiteAuditee:52`). Celui-ci devrait être incrémenté parce que la collection `paiements`, initialisée par `montantPaye()`, devient sale. Ce mécanisme est implicite et fragile.
  - Il n'y a pas non plus de clé d'idempotence : un double clic enregistre deux fois le même règlement tant que la somme ne dépasse pas le TTC.
  - Recommandation :
    - charger la facture avec `@Lock(PESSIMISTIC_WRITE)` dans `enregistrerPaiement` ;
    - accepter un en-tête `X-Cle-Operation`, comme pour les agences ;
    - ajouter un test d'intégration avec deux paiements parallèles.
- **I2 — PDF d'une facture inexistante ou d'une autre entreprise : 409 au lieu de 404** (`FacturePdfService.java:79`, `BusinessException`). Testé :
  ```
  GET /api/factures/1/pdf   (jeton ADMIN entreprise 6, facture de l'entreprise 1)
  -> 409 {"message":"Facture introuvable : 1"}
  ```
  Aucune fuite, le cloisonnement tient, mais le code est incohérent avec `/api/factures/1` (404). Utiliser `ResourceNotFoundException`.
- **I3 — Arrondi** : dans `facturerRecette:210-213,225`, le HT est déduit du montant encaissé puis le TTC est recalculé. Le règlement enregistré vaut ce TTC (écart possible de ±0,01) et non le montant réellement encaissé. Mieux : fixer le TTC égal au montant encaissé et calculer la TVA par différence.
- **I4 — Montant nul accepté** (`RecetteDto` `@PositiveOrZero`, `LigneRequete.prixUnitaire` `@PositiveOrZero`). Une facture émise à 0 reste indéfiniment « non payée » (`Facture.reevaluerStatut:140` exige `montantTtc > 0`) et pollue les créances. Refuser l'émission d'une facture à 0, ou la passer directement en PAYEE.

---

## Preuves des tests dynamiques (lecture seule, port 8085)

Jetons de test signés localement avec la clé de développement par défaut. L'API a été lancée sans `JWT_SECRET`, ce qui a déjà été signalé au socle. Les rôles sont relus en base par le filtre JWT.

```
GET /api/factures?size=1 (aucun jeton)                                -> 401
GET /api/factures?size=1 (ADMIN ent.1)                                -> 200
GET /api/factures?size=1 (CAISSIER ent.3, module parc inactif)        -> 403 "Le module Gestion de parc n'est pas active…"
Accès croisés, jeton ADMIN ent.6 sur les données de l'ent.1 :
  GET /api/factures/1                         -> 404
  GET /api/factures/1/pdf                     -> 409 "Facture introuvable : 1"  (I2)
  GET /api/recettes/1, /api/recettes/1/contrat-pdf -> 404
  GET /api/depenses/1, /api/depenses/1/justificatif -> 404
  GET /api/rentabilite/camions/1, /api/carburant/camions/1 -> 404 "Vehicule introuvable"
  GET /api/depenses/repartition/natures?camionId=1&…, /api/recettes/repartition?camionId=1&…,
      /api/recettes/disponibilite?camionId=1&…, /api/factures?clientId=1 -> 200 vides
```

Aucun compte CHAUFFEUR ou CAISSIER n'existe dans une entreprise abonnée au parc. Le refus de ces rôles est donc vérifié **par le code** : `@PreAuthorize(Autorisations.GESTION)` sur la classe de `FactureController:35`, `RecetteController:35`, `DepenseController:44`, `CarburantController:18`, `RentabiliteController:18`, `ChargesParcController:31` et `FournisseurParcController:29`.

## Points contrôlés sans constat

- Cloisonnement `@TenantId` sur `Facture`, `Paiement`, `Recette` et `Depense`. Les références (client, camion, mission, location, fournisseur, catégorie) sont chargées par des services filtrés : un identifiant d'une autre entreprise donne 404.
- Pas de mass assignment : DTO en `record`, recopiés champ par champ. `RecetteDto.Requete.factureId` est ignoré.
- Numérotation atomique : UPSERT `compteur_numero`, unique par `(entreprise_id, numero)` depuis V13.
- Totaux HT, TVA et TTC recalculés côté serveur, taux de TVA lu dans le paramétrage. Contraintes en base : `facture_montants_positifs`, `paiement_montant_positif`.
- Un règlement supérieur au reste est refusé, une facture réglée ne s'annule pas, une facture émise n'est plus modifiable.
- Index unique `uk_depense_recurrente_mois` : pas de double paiement d'un mois de récurrente.
- Fichiers servis par `FichierServi` : type tiré de l'extension, `nosniff`, `octet-stream` hors PDF et images. Le `window.open(blob)` de `depenses.ts:278` ne peut donc pas exécuter de HTML.
- Export CSV : `RapportService.echapper` neutralise `= + - @ \t \r`.
- Pas de requêtes natives ni JDBC dans `finance/`. `LIKE` paramétrés.
