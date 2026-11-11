# Audit sécurité 4/13 : module PARC (LJ SAAS, branche saas)

Date : 2026-11-11. Périmètre :
- backend `tg.parcauto.api.parc` (véhicules, chauffeurs, affectations, documents/justificatifs) ;
- les briques du parc situées ailleurs : `finance.depense` (dépenses générales, fournisseurs/dettes, règlements, charges et récurrentes) et `exploitation.client` (clients du parc) ;
- les écrans Angular correspondants (`pages/camions`, `vue-parc`, `chauffeurs`, `documents`, `depenses`, `fournisseurs-parc`, `shared/photo-vehicule`, `shared/dessin-vehicule`).

Méthode :
- revue statique ;
- tests dynamiques **en lecture seule** contre `http://localhost:8085` uniquement (GET, aucune écriture) ;
- requêtes SQL de lecture sur `parcauto_saas` pour choisir les identifiants de test ;
- jetons de test signés localement avec la clé de développement, déjà signalée dans l'audit du socle. Aucun mot de passe n'a été utilisé.

Exclus car déjà audités : socle, plateforme, paramétrage (voir la consigne).

---

## Synthèse

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 0 |
| Moyenne | 5 |
| Faible | 5 |
| Info | 4 |

**Points solides vérifiés :**
- Le cloisonnement entre entreprises tient. Il a été testé dynamiquement sur les véhicules, l'odomètre, les documents et leurs fichiers, les dépenses et leurs justificatifs, les affectations et la répartition des dépenses : toujours 404 ou liste vide.
- Aucune requête native ni JDBC dans le périmètre.
- Toutes les entités métier héritent de `BaseEntity` (`@TenantId`).
- Les index d'unicité sont composés avec `entreprise_id` : pas d'oracle d'existence d'une entreprise à l'autre.
- Traversée de chemin bloquée (`normalize` + `startsWith`), nom stocké en UUID.
- `FichierServi` : type déduit de l'extension, `nosniff`, `attachment` hors PDF et images.
- Le CSV des rapports neutralise `= + - @`.
- `DessinVehicule` n'injecte que des constantes.
- La photo du véhicule est validée par une expression régulière `data:image/...;base64`.

---

## MOYENNE

### M1. Lecture du parc ouverte à tous les profils, chauffeur compris, sans limite à son véhicule (confirmé par le code)
- **OWASP** : A01 Broken Access Control. **CVSS indicatif** : 5.4 (AV:N/AC:L/PR:L/UI:N/S:U/C:L/I:L/A:N).
- **Emplacement** :
  - `backend/src/main/java/tg/parcauto/api/parc/camion/CamionController.java:31-36` : pas de `@PreAuthorize` sur la classe, contrairement à `ChauffeurController`, `DocumentController` et `AffectationController` ;
  - points de lecture `:43-100` : `GET /api/camions`, `/{id}`, `/{id}/odometre`, `/statistiques/statuts`, référentiels.
- **Description** : toute personne authentifiée d'une entreprise abonnée au parc peut lire la flotte entière, quel que soit son profil : CHAUFFEUR, CAISSIER, SERVEUR, PREPARATEUR, MAGASINIER, ASSOCIE, CAISSIER_AGENCE, etc.
  - La fiche véhicule expose : `prixAchat`, `numeroChassis`, `numeroMoteur`, `gpsDeviceId`, `observations`, le kilométrage et le statut.
  - `/{id}/odometre` (`OdometreService.historique`) agrège :
    - les missions (numéro, nom du chauffeur) ;
    - les dépenses, y compris celles **en attente** (libellé, station, numéro, chauffeur) ;
    - les interventions et les trames GPS.

  Le chauffeur n'est pas limité à son véhicule : `Terrain` n'est appliqué qu'à `PATCH /kilometrage`.
- **Scénario** :
  - Un caissier du PDV (entreprise abonnée PDV + parc) appelle `GET /api/camions?size=2000` : il récupère le patrimoine (prix d'achat, numéros de châssis et de moteur) et les identifiants de traceurs.
  - Un chauffeur appelle `/api/camions/{id}/odometre` pour chaque véhicule : il suit les missions et les dépenses de ses collègues.
- **Preuve** :
  - Code : la classe n'a pas d'annotation de rôle, et `SecurityConfig` ne pose que `.anyRequest().authenticated()`.
  - Dynamique : aucune entreprise de test ne combine le module parc avec un profil non gestionnaire. Le test n'a donc confirmé que le contrôle de module : un caissier de l'entreprise 8, sans parc, reçoit `403 « Le module Gestion de parc n'est pas active »`.
- **Correctif** :
  - Poser `@PreAuthorize(Autorisations.GESTION)` sur la classe.
  - Ouvrir explicitement au chauffeur, en `TERRAIN`, un point `GET /api/camions/le-mien` (véhicule de l'affectation ou de la mission en cours) ; garder `PATCH /kilometrage` en `TERRAIN`.
  - Limiter `/odometre` à `GESTION`.
  - Projeter pour le chauffeur un DTO réduit, sans `prixAchat`, châssis, moteur ni traceur.

### M2. Le gestionnaire du parc lit, remplace et archive les pièces des clients et fournisseurs du PDV (confirmé par le code)
- **OWASP** : A01 (élévation horizontale entre modules). **CVSS indicatif** : 5.4.
- **Emplacement** :
  - `parc/document/DocumentSpecifications.java:22-27` : l'exclusion de `CLIENT_PDV`/`FOURNISSEUR_PDV` ne s'applique que si `proprietaireType` est nul ;
  - `parc/document/DocumentController.java:63-99` : `/{id}`, `/{id}/fichier`, `PUT /{id}`, `DELETE /{id}` sans contrôle du type de propriétaire ;
  - `parc/document/DocumentService.java:68-76` et `:123-133` : `verifierProprietaire` accepte `CLIENT_PDV` et `FOURNISSEUR_PDV`.
- **Description** :
  - Les pièces des clients de la variante Matériaux (CNI, etc.) et des fournisseurs du PDV (RCCM, NIF, contrats) sont normalement réservées au PDV, via `MateriauxController` et `FournisseurController`, qui vérifient le type.
  - Le point générique du parc, ouvert à `ADMIN, GESTIONNAIRE_PARC`, ne les filtre pas :
    - `GET /api/documents?proprietaireType=CLIENT_PDV` les liste ;
    - `GET /api/documents/{id}/fichier` les télécharge ;
    - `PUT /api/documents/{id}` (multipart) remplace le fichier, l'ancien étant supprimé physiquement (`:73`), ou rattache la pièce ailleurs ;
    - `DELETE` l'archive.
- **Scénario** : dans une entreprise abonnée au parc et au PDV, comme l'entreprise 4 de la base de test, un compte `GESTIONNAIRE_PARC` sans profil PDV télécharge les pièces d'identité des clients à crédit. Il peut aussi remplacer le contrat d'un fournisseur par un faux.
- **Preuve** :
  - Code ci-dessus.
  - Dynamique : l'entreprise 8, qui possède ces pièces, n'a pas le module parc (`403` module). Le cloisonnement entre entreprises est confirmé : depuis l'entreprise 1, `GET /api/documents/11` renvoie `404`.
- **Correctif** :
  - Dans `DocumentController`, refuser `CLIENT_PDV`/`FOURNISSEUR_PDV`, en lecture et en écriture, à l'entrée de `lister` (si le paramètre est fourni), `detail`, `telecharger`, `modifier` et `archiver`, ainsi que dans `Requete.proprietaireType`.
  - Mieux : un `Set<ProprietaireDocument> TYPES_PARC` vérifié dans le service, avec une méthode distincte pour l'usage PDV.

### M3. Justificatif d'une dépense validée remplacé ou retiré sans repasser par l'administrateur (confirmé par le code)
- **OWASP** : A04 Insecure Design (contournement d'un flux d'approbation). **CVSS indicatif** : 4.9 (I:H).
- **Emplacement** :
  - `finance/depense/DepenseService.java:296-311` (`joindre`) et `:333-341` (`retirerJustificatif`) : `charger(id)`, sans contrôle de statut ni re-validation ;
  - `finance/depense/DepenseController.java:161-180`, en `GESTION`.
- **Description** : avec la validation activée, l'administrateur approuve la dépense au vu de son justificatif. Ensuite, le gestionnaire peut :
  - remplacer ou supprimer la pièce, l'ancien fichier étant effacé du disque (`stockage.supprimer(ancien)`) ;
  - faire de même sur une dépense refusée ou annulée.

  Le montant validé reste, mais la preuve n'est plus celle que l'administrateur a vue, et l'original est détruit.
- **Scénario** :
  1. Le gestionnaire saisit une dépense de 450 000 F avec une vraie facture de 45 000 F retouchée, et la fait valider.
  2. Il remplace ensuite la pièce par un document neutre, ou la retire : la trace de la fraude disparaît.
- **Correctif** :
  - Interdire `joindre` et `retirer` hors de `ATTENTE_ADMIN` pour un non-administrateur, quand la validation est active.
  - Sinon, faire passer le remplacement par `correctionEnAttente`.
  - Ne jamais supprimer physiquement un justificatif d'une dépense validée : versionner les fichiers.

### M4. L'administrateur valide un contenu que le gestionnaire peut changer entre l'affichage et le clic (confirmé par le code)
- **OWASP** : A04. **CVSS indicatif** : 4.2.
- **Emplacement** : `finance/depense/DepenseService.java` :
  - `modifier` (`:180-207`) applique directement les changements sur une dépense `ATTENTE_ADMIN` (`appliquer` à `:197`) ;
  - `valider(id)` (`:238-262`) n'emporte ni version, ni montant attendu.
- **Description** : `@Version` (`EntiteAuditee:52`) ne protège que deux écritures simultanées dans la même fenêtre, pas l'écart entre l'écran de l'administrateur et sa décision.
- **Scénario** :
  1. L'administrateur ouvre « À valider » : une dépense de 20 000 F.
  2. Le gestionnaire la passe à 2 000 000 F, ou change de fournisseur avec `aPayer=true`.
  3. L'administrateur clique « Valider » : 2 000 000 F comptent et deviennent une dette réglable.
- **Correctif** :
  - `POST /{id}/validation` reçoit `{version}` ou `{montant, fournisseurId}` attendus, comparés côté serveur (409 en cas d'écart).
  - Ou geler la dépense `ATTENTE_ADMIN` pour le gestionnaire : il passe alors lui aussi par `correctionEnAttente`.

### M5. Course entre deux règlements fournisseur : paiement au-delà de la dette (confirmé par le code, non testé, car le test écrirait)
- **OWASP** : A04 (logique financière, condition de course). **CVSS indicatif** : 4.3.
- **Emplacement** : `finance/depense/FournisseurParcService.java:141-168`. Le reste dû est lu (`:149`) puis le règlement est inséré (`:168`), sans verrou sur la dépense. `regler` ne modifie pas `Depense`, donc `@Version` ne joue pas. `DepenseService.creer` (acompte) a le même schéma, en moins exposé.
- **Scénario** : deux envois simultanés de `POST /api/parc/fournisseurs/reglements {depenseId, montant = reste}`, double clic ou deux onglets. Les deux passent le contrôle : la dette est payée deux fois et le solde devient négatif.
- **Correctif** :
  - Charger la dépense avec `@Lock(PESSIMISTIC_WRITE)` dans `regler` et dans l'acompte de `creer`.
  - Ou incrémenter un `totalRegle` sur `Depense`, ce qui fait jouer `@Version`.
  - Ajouter une clé d'idempotence côté client.

---

## FAIBLE

### F1. Profil chauffeur : compte jamais relié à sa fiche ; plafond de relevé contournable (confirmé)
- **Emplacement** :
  - `security/Utilisateur.java:68-69` : `chauffeurId` n'est écrit nulle part. `grep setChauffeurId` ne donne aucun résultat, et ni `UtilisateurService` ni le paramétrage ne l'exposent.
  - `parc/camion/CamionService.java:119-137`.
- **Description** :
  - Aujourd'hui, tout compte CHAUFFEUR a `chauffeurId = null`. `Terrain` refuse donc toute action, ce qui est sûr mais rend la fonction morte, et M1 lui laisse toute la lecture.
  - Une fois la liaison livrée, le plafond de 5 000 km se contourne par appels répétés : 10 appels ajoutent 50 000 km, et aucun cumul quotidien n'est contrôlé. Cela fausse les échéances d'entretien et la consommation.
  - La clé étrangère `fk_utilisateur_chauffeur` ne vérifie pas que la fiche appartient à la même entreprise.
- **Correctif** :
  - Liaison compte–fiche par l'administrateur, avec contrôle que la fiche est chargée via `ChauffeurService.charger` (cloisonné).
  - Plafond cumulé par jour et par véhicule pour le chauffeur.
  - Clé étrangère composite `(entreprise_id, chauffeur_id)`.

### F2. Une dépense en attente, puis refusée, fait avancer définitivement le compteur du véhicule (confirmé)
- **Emplacement** : `finance/depense/DepenseService.java:172`. `mettreAJourCompteurCamion` est appelé quel que soit le statut, et `refuser` ne revient pas en arrière.
- **Scénario** : un gestionnaire, dont la dépense sera refusée, saisit un plein avec un relevé de 999 999 km. Le compteur ne peut plus redescendre (`CamionService.modifier` interdit la baisse), ce qui bloque la saisie normale.
- **Correctif** : n'appliquer le relevé qu'au passage `VALIDEE` (dans `valider`), et borner l'écart plausible.

### F3. Annulations sans administrateur malgré la validation (confirmé)
- **Emplacement** :
  - `DepenseService.annuler` (`:210`) : le gestionnaire annule une dépense déjà validée ;
  - `FournisseurParcService.annulerReglement` (`:171`) : il annule n'importe quel règlement.
- **Impact** : il efface des charges validées (résultat gonflé) ou rouvre des dettes, sans approbation. La trace reste visible.
- **Correctif** : quand `validationDepenses` est actif, réserver ces deux actions à `ADMINISTRATION`, ou les faire passer en demande à valider.

### F4. Téléversement des documents et justificatifs : contrôles minimaux (confirmé)
- **Emplacement** : `parc/document/StockageFichierService.java:27-63`, `DocumentService.java:73` et `:100-108`.
- **Constats** :
  1. Contrôle par extension seulement : ni signature magique, ni ré-encodage des images. Un `.pdf` peut contenir du JavaScript ou un HTML déguisé. Le risque est atténué par `nosniff` et le type tiré de l'extension, mais le PDF du justificatif s'ouvre en ligne (`afficher=true`) via une URL `blob:` du front (`pages/depenses/depenses.ts:277`).
  2. Pas de taille maximale propre à `/api/documents` : seul le plafond global de 60 Mo s'applique (déjà signalé). `DepenseService` limite, lui, à 15 Mo.
  3. Stockage non cloisonné par entreprise (`camion/{id}/uuid.ext`, `parc-depenses/{id}/...`) : la purge ou l'export d'une entreprise est plus difficile, et il n'y a pas de défense en profondeur.
  4. L'ancien fichier est supprimé avant la validation de la transaction (`modifier`, `joindre`) : en cas d'annulation, la pièce est perdue.
  5. Le fichier d'un document archivé reste téléchargeable par son identifiant.
  6. `.docx` et `.xlsx` sont acceptés sans analyse.
- **Correctif** :
  - Vérifier la signature (Apache Tika) et ré-encoder les images.
  - Fixer une limite par point (10 à 15 Mo).
  - Préfixer le chemin par `entreprise_id`.
  - Supprimer après la validation de la transaction (`TransactionSynchronization.afterCommit`).
  - Refuser le téléchargement des documents archivés, sauf pour l'administrateur.

### F5. Tri libre sur des champs internes ; tri inconnu renvoyant une erreur 500 (confirmé dynamiquement)
- **Emplacement** : tous les `Pageable` du module (`CamionController:43`, `DocumentController:47`, `ChauffeurController:43`, `DepenseController:56`).
- **Preuve** :
  - `GET /api/documents?sort=fichierChemin` → 200, trié sur le chemin interne ;
  - `GET /api/camions?sort=entrepriseId` → 200 ;
  - `GET /api/camions?sort=xyz` → `500 « Erreur technique du serveur »`.
- **Impact** : oracle d'ordre sur des champs non exposés ; journaux d'erreur pollués.
- **Correctif** : liste blanche des tris par écran, et `PropertyReferenceException` traduite en 400 dans `GlobalExceptionHandler`.

---

## INFO

- **I1.** Les recherches `LIKE` (`SpecificationUtils.motif`) n'échappent ni `%` ni `_` : `q=%` renvoie tout. Il n'y a pas d'injection, puisque les paramètres sont liés, mais il faut échapper avec `cb.like(..., '\\')`.
- **I2.** `ChauffeurDto.photoUrl` est une chaîne libre de 500 caractères (URL externe possible), contrairement au véhicule, validé par une expression régulière. Le champ n'est pas affiché aujourd'hui. Appliquer la même expression régulière que pour le véhicule.
- **I3.** Les référentiels `MarqueCamion`, `TypeCamion` et `CouleurCamion` sont globaux (`EntiteAuditee`, sans `@TenantId`). Ils sont en lecture seule et sans point d'écriture côté entreprise. C'est conforme, mais il ne faut jamais ouvrir leur écriture aux entreprises.
- **I4.** Front :
  - `vue-parc.html:155` `[src]="ligne.photoUrl"` et `photo-vehicule.ts:53` `[style.background-image]` sont sûrs tant que le serveur impose `data:image/...` ;
  - `DessinVehicule` (`bypassSecurityTrustHtml`) n'utilise que des constantes ;
  - aucun `[innerHTML]` ni `[href]` sur des données du parc.

---

## Preuves dynamiques (lecture seule, 8085)

Jetons de test signés localement avec la clé de développement, pour l'entreprise 1 (administrateur), l'entreprise 4 (administrateur, parc + PDV), l'entreprise 8 (caissier, gestionnaire-magasinier). Pas de mot de passe ni d'écriture.

| Requête | Compte | Résultat |
|---|---|---|
| `GET /api/documents/11` (document de l'entreprise 8) | entreprise 1 | 404 |
| `GET /api/documents/11/fichier` | entreprise 1 | 404 |
| `GET /api/camions/8` et `/8/odometre` (véhicule de l'entreprise 6) | entreprise 1 | 404 |
| `GET /api/depenses/77`, `/77/justificatif` (entreprise 1) | entreprise 4 | 404 |
| `GET /api/camions/4/odometre` (entreprise 1) | entreprise 4 | 404 |
| `GET /api/affectations?camionId=4`, `/api/documents?proprietaireType=CAMION&proprietaireId=4` | entreprise 4 | liste vide |
| `GET /api/camions` | caissier de l'entreprise 8 (sans parc) | 403 module |
| `GET /api/documents?proprietaireType=CLIENT_PDV` | gestionnaire de l'entreprise 8 (sans parc) | 403 module |
| `GET /api/camions?sort=xyz` | entreprise 1 | 500 |
| `GET /api/documents?sort=fichierChemin` | entreprise 1 | 200 |

## Trois priorités

1. **M1** : verrouiller `CamionController` en `GESTION` et créer un point « mon véhicule » pour le chauffeur.
2. **M2** : refuser les types `CLIENT_PDV`/`FOURNISSEUR_PDV` dans `/api/documents`.
3. **M3, M4 et M5** : figer le justificatif et le contenu validés, et verrouiller les règlements (verrou pessimiste ou version).

## État des surfaces (module parc)

| Surface | État |
|---|---|
| Authentification | hors périmètre (socle) ; RAS dans le module |
| Autorisation et cloisonnement | cloisonnement entre entreprises solide (vérifié) ; rôles insuffisants (M1, M2) ; périmètre chauffeur inopérant (F1) |
| Argent (dépenses, dettes) | contournements du flux de validation (M3, M4, F3) ; course sur les règlements (M5) |
| Points publics | aucun dans le module |
| Injection | RAS : Criteria/JPQL paramétrés, CSV neutralisé, pas de requête native ; tri libre (F5) |
| Fichiers | traversée bloquée ; contrôles de contenu et de taille faibles (F4) |
| Infra | hors périmètre |
