# Audit de sécurité ciblé : fonds de réserve du point de vente (2026-11-13)

Périmètre : modifications non commitées de la branche `saas` (migration V85, package
`pointdevente.reserve`, `MargeVentes`, `DepensePdvService`, `FournisseurService`,
`ReglagePdvService`, `TresorerieService`, `RapportPdvService`, frontend `fonds-reserve`,
`choix-sortie`, `reglages`). Revue statique complète du diff, plus des contrôles en base en
lecture seule.

**Tests dynamiques HTTP : non réalisés.** L'API locale ne répondait pas sur le port 8085 au
moment de l'audit (connexion refusée sur `/api/pdv/fonds-reserve` et sur
`/actuator/health`). Conformément aux règles, je ne l'ai pas démarrée. La base
`parcauto_saas` est déjà en V85. Les constats ci-dessous sont donc **confirmés par lecture du
code**. Les requêtes de reproduction sont fournies pour être rejouées une fois l'API relancée.
Le seul test exécuté est celui de la requête native, directement en SQL et en lecture seule.

---

## Constats

### M-1 : Après approbation, un gérant peut annuler ou modifier une dépense payée par le fonds, ce qui contourne la validation de l'administrateur

- **Gravité** : Moyenne (CVSS 3.1 indicatif 5.4 : AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:L/A:L), OWASP A01 (contrôle d'accès défaillant) et A04 (conception non sûre)
- **Statut** : confirmé (code)
- **Emplacement** :
  - `backend/src/main/java/tg/parcauto/api/pointdevente/depense/DepensePdvService.java:351-368` (`modifier`), `:451-465` (`annuler`), `:469-501` (`joindre`, `retirerJustificatif`) ;
  - `DepensePdvController.java:85-98` (PUT `/{id}`, POST `/{id}/annulation`), exposé à `PDV_GESTION`, soit ADMIN et GERANT.
- **Description** : `annuler` et `modifier` ne vérifient ni le mode (`RESERVE`), ni le statut
  `VALIDE`, ni l'auteur, ni le rôle au-delà de `PDV_GESTION`. N'importe quel gérant de
  l'entreprise peut donc :
  1. **annuler une dépense RESERVE approuvée par l'administrateur, ou créée directement par
     lui**. `totalPar(RESERVE, VALIDE)` diminue, si bien que le solde et le disponible du fonds
     remontent alors que l'argent est réellement sorti. La ligne disparaît aussi du journal du
     fonds, puisque `payeesPar` filtre sur `VALIDE`. Le fonds devient surévalué et l'admin
     approuvera ensuite d'autres dépenses sur un disponible faux ;
  2. **modifier le bénéficiaire, le libellé, la catégorie, la date ou le justificatif** d'une
     dépense RESERVE déjà approuvée. Exemple : l'admin approuve « Réparation toiture,
     bénéficiaire Entreprise X », puis le gérant remplace le bénéficiaire par son propre nom et
     le justificatif ;
  3. faire de même **entre l'affichage et le clic d'approbation** (TOCTOU). Le POST
     `/validations-caisse/depenses/{id}/validation` n'indique pas la version vue par l'admin,
     qui approuve donc la version courante et non celle qu'il a lue. Le montant et le mode, eux,
     ne sont pas modifiables : c'est correct.

  Ces actions sont tracées par `EcouteurAudit`, ce qui permet de les détecter, mais rien ne les
  empêche.
- **Reproduction** (à rejouer avec un compte GERANT, une fois l'API relancée) :
  ```
  POST /api/pdv/depenses/{idDepenseReserveValide}/annulation   {"motif":"x"}   -> 200 attendu (faille)
  GET  /api/pdv/fonds-reserve   -> "solde" et "disponible" augmentés du montant annulé
  PUT  /api/pdv/depenses/{id}   {"categorieId":..,"libelle":"..","beneficiaire":"Moi"}  -> 200
  ```
- **Correctif** :
  - dans `annuler`, `modifier`, `joindre` et `retirerJustificatif` : si
    `d.getMode() == RESERVE && d.getStatut() == VALIDE`, exiger
    `CircuitDepensesCaisse.exigerAdministrateur(u)` (passer le principal au service) ;
  - si le statut est `ATTENTE_ADMIN`, n'autoriser que l'auteur ou un admin, et remettre le
    statut en attente avec un nouveau visa après toute modification ;
  - l'annulation d'une dépense RESERVE validée devrait demander un motif, et idéalement être
    réservée à l'admin quel que soit le mode, par cohérence avec la règle « annulations après
    clôture refusées » ;
  - approbation : faire envoyer au client la `version` vue (`?version=` ou `If-Match`) et
    refuser l'opération si elle a changé.

### M-2 : La base de calcul du fonds n'est pas figée : un gérant peut gonfler le disponible, et le verrou ne protège pas l'invariant « solde ≥ 0 »

- **Gravité** : Moyenne (CVSS indicatif 4.3 : AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:L/A:N), OWASP A04
- **Statut** : confirmé (code)
- **Emplacement** :
  - `reserve/FondsReserveService.java:177-215` (`dotations` recalculées à chaque lecture depuis l'origine) et `:166-170` (`utilisable`) ;
  - `depense/DepensePdvService.java:351-366` (`modifier` : n'importe quelle date passée) et `:277-282` (`creer` : date passée libre) ;
  - `depense/DepensePdvRepository.java` (`totauxParJourHors` filtre sur `dateDepense`).
- **Description** : le disponible vaut les versements des **mois terminés**, recalculés à
  partir des ventes et des dépenses datées. Or un gérant peut, sans passer par
  `exigerDisponible` ni par le verrou :
  - **déplacer la date** d'une dépense ordinaire (BANQUE, CAISSE ou COFFRE) d'un mois clos vers
    une date antérieure à l'origine du fonds. La dépense sort alors de la base, le bénéfice net
    du mois clos et sa dotation augmentent, et le disponible avec. Il peut aussi la déplacer
    vers le mois en cours, qui n'est pas encore dépensable ;
  - **annuler** une dépense ordinaire d'un mois clos, avec le même effet ;
  - puis déposer une dépense RESERVE, que l'admin approuvera sur ce disponible gonflé.

  À l'inverse, une dépense antidatée dans un mois clos, ou l'annulation d'une vente, font baisser
  les dotations **après** une dépense du fonds : le solde passe sous zéro. Le verrou
  `PESSIMISTIC_WRITE` sur `pdv_reglage` sérialise correctement créations, approbations et
  désactivation. En revanche, aucun de ces chemins ne le prend, et ils peuvent s'exécuter entre
  le contrôle et le commit d'une approbation. Le commentaire « deux dépenses simultanées ne
  dépassent pas le disponible » est exact. Mais l'invariant global, un fonds jamais négatif,
  n'est pas garanti.
- **Reproduction** (GERANT, fonds actif depuis au moins un mois clos) :
  ```
  GET /api/pdv/fonds-reserve                              -> disponible = D
  PUT /api/pdv/depenses/{idDepenseBanqueMoisClos} {"categorieId":..,"libelle":"..","dateDepense":"2020-01-01"}
  GET /api/pdv/fonds-reserve                              -> disponible = D + pourcent * montant
  ```
- **Correctif** (par ordre de préférence) :
  1. **figer les dotations des mois clos**. Une table `pdv_fonds_reserve_dotation` serait
     remplie à la première lecture qui suit la fin du mois, ou par une tâche planifiée, sous le
     verrou. Toute correction postérieure (annulation, date changée, vente annulée) serait
     imputée comme ajustement au mois courant, ce qui est aussi plus juste comptablement ;
  2. à défaut, quand le fonds est actif, refuser la création ou la modification d'une dépense
     dont la date (ancienne ou nouvelle) tombe dans un mois clos, sauf pour un ADMIN, et
     interdire de dater une dépense avant l'origine du fonds ;
  3. faire prendre le verrou `verrouillerFondsReserve()`, ou un verrou dédié, aux chemins qui
     changent la base (annulation et modification de dépense, annulation de vente), au moins
     quand le fonds est actif.

### F-1 : Une dépense RESERVE de l'administrateur est directe et peut être antidatée

- **Gravité** : Faible (CVSS indicatif 2.7), OWASP A04
- **Statut** : confirmé (code)
- **Emplacement** : `DepensePdvService.java:279-282` et `:310-323`
- **Description** : la dépense de l'admin passe en `VALIDE` sans second contrôle. C'est le
  choix fonctionnel : l'admin ne peut pas « approuver sa propre dépense », puisqu'elle n'est
  jamais en attente, mais personne ne la contrôle non plus. Sa `dateDepense` peut en outre être
  fixée dans le passé, avant même l'origine du fonds. Le contrôle du disponible ne dépend pas de
  la date, mais le journal `JOURNAL_FONDS_RESERVE` place le débit à cette date. Cela fausse le
  « solde au début de la période » et peut faire apparaître un solde négatif avant la première
  dotation.
- **Correctif** :
  - pour le mode RESERVE, imposer `dateDepense = aujourd'hui`, comme le fait déjà l'approbation
    (`DepensePdvService.java:407`) ;
  - en option, prévoir un seuil au-delà duquel une seconde personne doit approuver, ou au
    minimum une notification aux associés ou au super-admin.

### F-2 : Un gérant peut bloquer le fonds avec des demandes en attente

- **Gravité** : Faible (CVSS indicatif 3.1 : A:L), OWASP A04
- **Statut** : confirmé (code)
- **Emplacement** :
  - `FondsReserveService.java:137-148` (le montant en attente est soustrait du disponible) ;
  - `ReglagePdvService.java:102-106` (désactivation refusée tant que des demandes attendent).
- **Description** : un gérant peut déposer autant de demandes RESERVE qu'il veut, chacune
  jusqu'au disponible restant. Elles réservent la totalité du fonds, ce qui bloque les dépenses
  de l'admin, et empêchent sa désactivation. L'admin peut toutes les refuser une à une, d'où
  l'impact faible.
- **Correctif** :
  - limiter le nombre ou le cumul des demandes en attente par gérant ;
  - prévoir un refus groupé, ou une expiration automatique après N jours ;
  - à la désactivation, proposer de refuser d'office les demandes en attente.

### F-3 : Les réglages du fonds sont exposés à tous les profils du point de vente

- **Gravité** : Faible (CVSS indicatif 3.1 : C:L), OWASP A01
- **Statut** : confirmé (code)
- **Emplacement** : `reglage/ReglagePdvController.java:29` (GET `/api/pdv/reglages` ouvert à
  CAISSIER, SERVEUR, PREPARATEUR, MAGASINIER et ASSOCIE) et `ReglagePdvService.java:52-58`
  (`Reglages` contient `fondsReserveActif` et `fondsReservePourcent`).
- **Description** : un caissier ou un serveur apprend que l'entreprise met de côté X % de son
  bénéfice. Aucun montant n'est exposé : le solde n'est lisible que par ADMIN et GERANT.
- **Correctif** : renvoyer aux profils autres qu'ADMIN et GERANT un DTO réduit, avec seulement
  le booléen nécessaire au menu, voire rien du tout puisque leur menu n'affiche pas le fonds.

### F-4 : Les dotations sont visibles d'un gérant dans le journal de banque, alors que le compte de l'entreprise est réservé à l'admin

- **Gravité** : Faible / Info (incohérence antérieure, aggravée)
- **Statut** : confirmé (code)
- **Emplacement** :
  - `rapport/RapportController.java:38` : `PDV_GESTION` pour tous les rapports, dont `JOURNAL_BANQUE` et `JOURNAL_FONDS_RESERVE` ;
  - `tresorerie/TresorerieController.java:161` : `compte-entreprise` réservé à `ADMINISTRATION` ;
  - `RapportPdvService.java:860-869` : les dotations ajoutées au journal de banque.
- **Description** : le gérant n'a pas accès au compte de l'entreprise, mais il peut lire le
  journal de banque, qui affiche désormais les dotations, ainsi que le journal du fonds. Pour le
  journal du fonds, c'est cohérent avec la décision « fonds lisible par ADMIN et GERANT ». Pour
  le journal de banque, c'est une incohérence antérieure qui expose en plus le bénéfice net
  mensuel, présent dans les libellés des dotations.
- **Correctif** : décider de la règle. Si le compte de l'entreprise est réservé à l'admin,
  filtrer `JOURNAL_BANQUE` dans `catalogue()` et `calculer()` pour les non-admins.

### I-1 : Cloisonnement de la requête native : correct

- **Statut** : confirmé (code et test SQL en lecture seule)
- **Emplacement** : `vente/VenteRepository.java:309-323` et `vente/MargeVentes.java:46`
- **Constat** : la requête filtre `v.entreprise_id = :entreprise`, avec la valeur de
  `ContexteEntreprise.exiger()`. Sans entreprise dans le contexte, une `IllegalStateException`
  est levée : l'échec est sûr, le traitement ne renvoie jamais les données de toutes les
  entreprises. La sous-requête sur `pdv_ligne_vente` est liée par `vente_id` à une vente déjà
  filtrée. Contrôle en base, en lecture seule :
  ```
  requete_native (entreprise 8) | 5 jours | 3 815 600.00
  toutes_entreprises            |         | 4 987 900.00
  entreprise_8_directe          |         | 3 815 600.00
  lignes de vente dont l'entreprise differe de celle de la vente : 0
  ```
  Les paramètres sont liés et non concaténés : aucune injection possible.
- **Recommandation (défense en profondeur)** : en mode système, les requêtes JPQL voisines
  voient toutes les entreprises (RACINE), alors que la requête native ne voit que l'entreprise
  courante. Aucun appel en mode système n'existe aujourd'hui. Ajouter
  `if (ContexteEntreprise.systeme()) throw new IllegalStateException(...)` dans
  `MargeVentes.parJour` et `FondsReserveService.exigerDisponible` éviterait qu'un futur
  traitement système verrouille toutes les lignes `pdv_reglage` ou additionne les dépenses de
  toutes les entreprises.

### I-2 : Divers

- Le message de `exigerDisponible` (`FondsReserveService.java:145`) indique le disponible. Il
  n'est atteignable qu'après le contrôle du rôle ADMIN ou GERANT, qui lisent déjà le fonds :
  acceptable. Les autres messages ne révèlent rien de sensible.
- `frontend/angular.json` : ajout de `"analytics": "16ab68e9-…"`, qui active la télémétrie de
  la CLI Angular. À ne pas commiter, ou à remplacer par `"analytics": false`.
- `frontend/src/environments/environment.ts` : `http://${hostname}:8085/api` est marqué « non
  commité ». La production utilise bien `'/api'` (`environment.prod.ts`). Vérifier qu'il n'est
  pas commité par erreur.
- Coût des calculs : `dotations()` relit à chaque appel toutes les ventes et dépenses depuis
  l'origine (fonds, compte de l'entreprise, journaux). Il n'y a que deux requêtes, mais leur
  volume grandit indéfiniment. La correction M-2.1 (mois figés) règle aussi ce point.

---

## Points vérifiés sans défaut

- **Lecture du fonds** : GET `/api/pdv/fonds-reserve` exige `PDV_GESTION` (ADMIN, GERANT) et
  `@ModuleRequis(POINT_DE_VENTE)`. Un caissier, un serveur, un préparateur, un magasinier ou un
  associé reçoit 403. Côté Angular, la garde `roleGuard('ADMIN','GERANT')` n'est que de confort.
- **Création RESERVE** : le contrôleur exige déjà `PDV_GESTION`, et le service vérifie en plus
  ADMIN ou GERANT (`DepensePdvService.java:313-316`), avant toute lecture du disponible. Aucun
  autre point d'entrée n'appelle `DepensePdvService.creer`.
- **Validation et refus** : une dépense RESERVE d'un gérant naît en `ATTENTE_ADMIN`. Un gérant
  ne peut ni la valider ni la refuser (`:391-395`, `:432-436`). Seul l'admin l'approuve, après
  un nouveau contrôle sous verrou (`:408-414`).
- **Courses** :
  - créations, approbations et changement de réglage prennent le même verrou
    `SELECT … FOR UPDATE` sur `pdv_reglage`, dans la transaction de l'appelant ;
  - en READ COMMITTED, les totaux relus après le verrou voient les commits précédents ;
  - deux approbations simultanées de la même dépense sont arrêtées par le `@Version`
    (`EntiteAuditee`) ;
  - le contrôle des demandes en attente avant une désactivation est fait sous le verrou ;
  - pas d'ordre de verrous croisé : numérotation puis réglage, réglage seul ailleurs.
- **Fournisseurs** : `FournisseurService.java:312` refuse RESERVE. `ModeSortie.propose()`
  n'est utilisé nulle part ailleurs : aucun autre paiement n'accepte RESERVE par erreur.
- **Entrées** :
  - montant `@NotNull @Positive`, arrondi à 2 décimales ;
  - taux `@DecimalMin("0.01")` et `@DecimalMax("100")`, plus les CHECK en base
    (`ck_pdv_reglage_fonds_reserve`, `ck_pdv_fonds_reserve_periode_pourcent`), avec une période
    ouverte unique par entreprise (index unique partiel) ;
  - modification du réglage réservée à `ADMINISTRATION`.
- **Cloisonnement JPQL** : `PeriodeFondsReserve` hérite de `BaseEntity` (`@TenantId`), et les
  nouvelles requêtes JPQL et dérivées sont filtrées par Hibernate.
- **Exports** : `ExportRapport.cellule` neutralise `= + - @`, tabulation et retour chariot. Les
  libellés saisis (dépense, bénéficiaire, catégorie) qui entrent dans le journal du fonds ne
  peuvent donc pas injecter de formules.
- **Audit** : `DepensePdv`, `ReglagePdv` et `PeriodeFondsReserve` sont tracés par
  `EcouteurAudit`, puisque seules quelques entités techniques en sont exclues.
- **XSS** : pas de `[innerHTML]` ni de `bypassSecurityTrust*` dans la page `fonds-reserve`, dans
  `choix-sortie` ou dans le bloc des réglages.

## Synthèse

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 0 |
| Moyenne | 2 (M-1, M-2) |
| Faible | 4 (F-1 à F-4) |
| Info | 2 (I-1, I-2) |

**Priorités**
1. M-1 : réserver à l'admin l'annulation et la modification (y compris le justificatif) d'une
   dépense RESERVE validée, et lier l'approbation à la version affichée.
2. M-2 : figer les dotations des mois clos, ou interdire les dates de dépense dans un mois clos
   quand le fonds est actif.
3. F-1 : imposer la date du jour à une dépense RESERVE ; F-3 : réduire les réglages exposés aux
   autres profils.

**État des surfaces**
- Authentification : inchangée par la fonctionnalité.
- Autorisation et cloisonnement :
  - lecture, création et approbation correctes ;
  - faille de séparation des tâches après approbation (M-1) ;
  - cloisonnement entre entreprises correct, y compris sur la requête native.
- Argent : verrou correct pour les dépenses simultanées ; base de calcul manipulable et
  invariant de solde non garanti (M-2).
- Points publics : aucun ajouté.
- Injection : aucune (paramètres liés, exports neutralisés).
- Infra et configuration : `angular.json` (analytics) et `environment.ts` (local) à ne pas commiter.
