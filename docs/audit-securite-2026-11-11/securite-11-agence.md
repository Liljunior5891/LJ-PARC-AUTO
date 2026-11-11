# Audit de sécurité 11/13 : module Agences multiservices

- Périmètre : `backend/src/main/java/tg/parcauto/api/agence/**` (≈ 11 600 lignes), migrations V44 à V64 et V77, écrans `frontend/src/app/pages/agences/**`, `core/services/agences*.ts`, `layout/etat-hors-ligne-agences.ts`.
- Branche `saas`, état du 2026-11-11. L'audit n'a modifié aucun fichier du dépôt.
- Tests dynamiques : uniquement des sondes **sans jeton** sur `http://localhost:8085`, plus des lectures SQL en lecture seule sur `parcauto_saas`. Je n'ai fait aucune connexion avec les comptes de test : `AuthController` écrit dans `journal_activite` à chaque connexion, et la consigne interdit d'écrire dans le journal d'audit. Les constats sont donc **confirmés par lecture du code**, sauf mention « à vérifier ».

## Points solides (vérifiés)

- **Registre.** `RegistreService.ecrire` verrouille les comptes (`PESSIMISTIC_WRITE`, dans l'ordre des identifiants), relit les soldes sous verrou et contrôle le découvert après regroupement. Le double débit concurrent du float ou de la caisse est bien bloqué.
- **Annulations.**
  - Une seule annulation possible par écriture : index unique `uk_agc_ecriture_annulee`, plus `@Version` sur toutes les entités.
  - Une écriture déjà annulée ne s'annule pas une seconde fois.
  - Le verrou de journée clôturée est contrôlé sur la journée d'origine.
  - Après fermeture de la caisse, l'annulation est refusée pour les transactions, ventes de cartes et dépenses.
- **Cloisonnement.**
  - `@TenantId` sur toutes les entités `agc_*`.
  - `PerimetreAgence.exiger` est appelé sur chaque identifiant d'agence, de session, de transaction, de mouvement, de dépense, de versement et de compte.
  - Les comptes cités sont contrôlés contre l'agence (`compte.getAgenceId().equals(...)`).
  - Je n'ai trouvé aucun IDOR entre entreprises ni entre agences.
- **Caissier.**
  - Il ne travaille que sur sa caisse ouverte (`sessions.ouverte(agence, moi)`).
  - L'historique, le tableau de bord et la liste des dépenses sont filtrés sur ses propres opérations s'il n'a pas `HISTORIQUE_COMPLET`.
  - Seul le caissier concerné peut soumettre le comptage de sa caisse.
- **Montants.**
  - Les montants sont des `Long` en `@Positive`, protégés en base par des `CHECK (montant > 0)`.
  - Les commissions viennent du barème côté serveur. La modification exige `COMMISSION_MODIFIER` et un motif, et elle est tracée.
  - Les taux sont bornés entre 0 et 100 %, avec 4 décimales au plus, et calculés en `BigDecimal` avec `longValueExact`.
- **Idempotence en ligne.** Avec `X-Cle-Operation`, la clé est enregistrée dans la même transaction que l'écriture, sous la contrainte unique `(entreprise_id, cle)`. Il n'y a pas de double enregistrement possible.
- **Journal.** Les déclencheurs `tg_agc_*_immuable` sont présents et actifs (`tgenabled = O`) sur `agc_ecriture`, `agc_ligne_registre` et `agc_journal_audit`.
- **Fichiers.**
  - Liste blanche d'extensions, nom de stockage UUID, garde contre la traversée de chemin.
  - `FichierServi` fixe le type d'après l'extension et envoie `nosniff`.
  - Logos : data URL strictement limitées à JPEG, PNG et WebP (pas de SVG).
- **Front.** Aucun `innerHTML`, `bypassSecurityTrust*` ni `document.write` dans les pages agences. Les justificatifs s'ouvrent en blob avec un type serveur sûr.
- **Sans jeton.** Toutes les routes sondées répondent `401`, y compris `POST /api/agences/hors-ligne/synchronisation`.

---

## ÉLEVÉ

### E1. Reprise d'un conflit hors ligne : exécution d'une saisie forgée avec les droits du superviseur (« confused deputy »)

- **Gravité** : Élevée. CVSS 3.1 indicatif 6,8 (AV:N/AC:L/PR:L/UI:R/S:U/C:N/I:H/A:L).
- **Catégories OWASP** : A01 Broken Access Control, A04 Insecure Design.
- **Emplacement** :
  - `agence/horsligne/HorsLigneService.java:62` : `OperationEnvoyee` (`resume` libre, `donnees` libres) ;
  - `:69` : `ConflitDto` ne contient pas `donnees` ;
  - `:252-255` : `reprendre()` rejoue `o.getDonnees()` avec `executer(..., superviseur)` ;
  - `:164-170` : cas `DEPENSE` ;
  - `frontend/.../pages/agences/hors-ligne/conflits-hors-ligne.ts:40-48` : seuls `c.resume` et `c.motif` sont affichés avant le bouton « Reprendre maintenant ».

**Description.** Une opération hors ligne refusée est conservée telle quelle (JSON brut) et le superviseur peut la « reprendre ». La reprise :

1. exécute le JSON envoyé par l'appareil, avec **les droits et la caisse du superviseur** ;
2. alors que l'écran ne lui montre que le `resume`, un **texte libre choisi par l'émetteur**. Le superviseur ne voit ni le montant réel, ni le compte débité, ni la commission.

**Scénario (caissier malveillant, aucun outil spécial)** :

1. Le caissier appelle `POST /api/agences/hors-ligne/synchronisation` avec ce contenu :
   ```json
   [{"cle":"x1","type":"DEPENSE","saisieLe":"2026-11-11T08:00:00Z",
     "resume":"Dépense Fournitures 45 000 F",
     "donnees":{"agenceId":1,"categorieId":7,"libelle":"Fournitures","montant":1500000,
                "compteId":<id du compte BANQUE ou caisse générale>,"beneficiaire":"Complice"}}]
   ```
2. Le serveur refuse : « Une dépense de caissier est limitée à 50 000 F : demandez à votre superviseur ». L'opération devient un CONFLIT, et ce message rend la demande crédible.
3. Le superviseur voit « Dépense Fournitures 45 000 F » et clique sur « Reprendre ». `DepenseAgenceService.creer` s'exécute pour lui :
   - pas de plafond caissier ;
   - `compteId` est pris dans le JSON ;
   - sur un compte non-cash (`BANQUE`, `FLOAT`, `COMMISSIONS`), ou si le réglage de validation est désactivé (cas actuel de la base locale, `validation_depenses_caisse = f`), **1 500 000 F sortent immédiatement**, au nom du superviseur.
4. Même principe pour `TRANSACTION`. Une commission forcée (`commission` + `motifCommission`), refusée au caissier faute de `COMMISSION_MODIFIER`, est acceptée à la reprise. L'opération porte sur la caisse générale, avec un montant pouvant aller jusqu'au maximum par transaction.

**Correctif** :
- À la reprise, **exécuter avec l'identité et les droits de l'auteur d'origine** (au minimum, refaire `droits` et plafond pour `auteurId`). Ou bien n'autoriser la reprise que pour des causes « levables » (solde insuffisant, caisse fermée), en refusant toute reprise d'un conflit né d'un refus de droit ou de plafond.
- **Ne plus accepter `resume` du client** : le construire côté serveur à partir des `donnees` validées (type, montant, compte, service). Ajouter au `ConflitDto` le détail déchiffré (montant, compte, commission, bénéficiaire) et l'afficher dans la modale de confirmation.
- Pour `DEPENSE`, ignorer `compteId` à la reprise : reprendre la caisse de l'auteur, ou exiger que le superviseur la choisisse explicitement.

### E2. `HorsLigneController` sans `@ModuleRequis` : tout compte authentifié peut écrire dans les tables agences et dans le journal d'audit immuable, sans limite de volume

- **Gravité** : Élevée. CVSS 3.1 indicatif 6,5 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:L/A:H).
- **Catégories OWASP** : A01, A04 et API4 (consommation de ressources non bornée).
- **Emplacement** :
  - `agence/horsligne/HorsLigneController.java:21-31` : seul contrôleur du paquet sans `@ModuleRequis(ModuleApplicatif.AGENCES)`, et `synchroniser` n'a pas de `@PreAuthorize` ;
  - `HorsLigneService.java:62` : `List<OperationEnvoyee>` sans `@Size`, `donnees` (`JsonNode`) non bornée ;
  - `:126-140` : chaque refus crée une ligne `agc_operation_hors_ligne` et une ligne **`agc_journal_audit`** contenant le texte de l'émetteur (`resume`, motif).

**Preuve** : `grep -L ModuleRequis agence/**/*Controller.java` ne renvoie que `HorsLigneController.java`. `ControleModuleInterceptor` ne fait rien quand l'annotation est absente (`if (requis == null) return true;`).

**Scénario** :
- Un CHAUFFEUR, un GERANT, un caissier de PDV ou un ASSOCIÉ de n'importe quelle entreprise (même sans module Agences) envoie une seule requête contenant des dizaines de milliers d'opérations, chacune avec un `agenceId` choisi.
- Chaque opération est refusée (« Vous n'avez pas le droit ») et devient un CONFLIT, plus une ligne d'audit **non supprimable** (déclencheur `agc_interdire_modification`).
- Effets :
  1. saturation durable de la base et du journal de l'entreprise, sans purge possible ;
  2. pollution de la file des conflits du superviseur, ce qui alimente E1 ;
  3. un ADMIN d'une entreprise dont le module Agences est retiré peut encore saisir transactions et dépenses par ce chemin.

**Correctif** :
- Ajouter `@ModuleRequis(ModuleApplicatif.AGENCES)` sur la classe, et `@PreAuthorize("@droitsAgence.a('TRANSACTION_SAISIR') or @droitsAgence.a('DEPENSE_SAISIR')")` sur `synchroniser`.
- Borner la liste (`@Size(max = 200)`) et la taille de `donnees` (par exemple 8 Ko).
- Refuser un `agenceId` hors périmètre **avant** de créer le conflit (rejet simple, sans trace immuable).
- Ajouter un limiteur par utilisateur (`LimiteurRequetes`).

---

## MOYEN

### M1. Contournement de la validation des dépenses de caisse par un compte non-cash ou par un virement interne

- **Gravité** : Moyenne. CVSS 5,4. OWASP A04.
- **Emplacement** :
  - `agence/depense/DepenseAgenceService.java:248-250` : `etapeInitiale` ne soumet à validation que la famille `CASH` ;
  - `agence/mouvement/MouvementService.java:300-320` : `verifierType`, où `TRANSFERT_INTERNE` passe par `default -> {}` (aucune contrainte) ;
  - `MouvementService.creer` : application immédiate sous le seuil (500 000 F par défaut).

**Scénario** (réglage « validation des dépenses de caisse » activé) :
1. Le superviseur fait un `VERSEMENT_BANCAIRE` ou un `TRANSFERT_INTERNE` de 450 000 F de la caisse générale vers le compte `BANQUE` (ou `FONDS_EXPLOITATION`). C'est sous le seuil, donc **appliqué sans second visa**.
2. Il saisit ensuite une dépense de 450 000 F sur ce compte : `etapeInitiale` renvoie `null`, et la dépense est **écrite immédiatement**, sans approbation de l'administrateur.

Le contrôle voulu (« rien ne sort avant l'admin ») est donc contourné en deux appels. C'est la même famille que le constat PDV (« règlements fournisseurs »), mais il s'agit d'un emplacement propre aux agences.

**Correctif** : appliquer le circuit de validation à **toute** dépense saisie par un non-administrateur quand le réglage est actif, quel que soit le compte. Ou bien rendre le réglage « dépenses hors caisse » explicite. Interdire aussi au non-admin les dépenses sur `FLOAT`, `RECHARGE` et `COMMISSIONS` (argent virtuel des opérateurs).

### M2. Fermeture de caisse : le superviseur ferme sa propre caisse et régularise seul un écart illimité

- **Gravité** : Moyenne. CVSS 5,0. OWASP A04 (séparation des tâches).
- **Emplacement** :
  - `agence/caisse/SessionCaisseService.java:61` : `PROFILS_CAISSE` contient `SUPERVISEUR_AGENCE` ;
  - `:233` : `ouvrir`, où `caissierId` peut être le superviseur lui-même ;
  - `:351-380` : `fermer` ne vérifie pas `s.getCaissierId() != superviseur.getId()`. Un écart non nul n'exige qu'un texte de justification, puis une écriture `REGULARISATION` est passée immédiatement.

**Scénario** : le superviseur ouvre sa propre caisse, encaisse, prélève 300 000 F en espèces, puis ferme sa caisse en comptant 300 000 F de moins avec la justification « erreur de rendu ». L'écart est passé en perte, **sans aucun second regard**. À l'inverse, un `MOUVEMENT` de type `REGULARISATION` exige toujours une autre personne (`MouvementService.valider`). Le contrôle est donc incohérent.

**Correctif** : refuser `fermer` et `rejeter` quand le superviseur est le titulaire de la caisse (sauf ADMIN). Au-delà d'un seuil d'écart (réglage), laisser l'écart en attente de validation, comme une régularisation de mouvement.

### M3. Annulation et saisie de mouvements sur une caisse de caissier fermée ou soumise

- **Gravité** : Moyenne. CVSS 4,6. OWASP A04.
- **Emplacement** :
  - `agence/mouvement/MouvementService.java:177-188` : `annuler` appelle `verrou.exigerOuverte` mais **ni** `sessions.exigerSessionOuverte` **ni** `exigerCaisseOuverte` ;
  - `creer` (`compte(agence, id)`) accepte un compte `CAISSE_CAISSIER` quel que soit l'état de la session.

**Description** : la règle « annulations après fermeture refusées » (revue du 2026-11-10) est appliquée aux transactions, aux cartes et aux dépenses, mais **pas aux mouvements**.

**Scénario** :
1. Remise partielle « caisse de A → caisse générale, 100 000 F ».
2. La caisse de A est fermée.
3. Le superviseur annule la remise : +100 000 F réapparaissent dans la caisse de A, alors qu'aucune session n'est ouverte.
4. À la session suivante, le montant attendu contient 100 000 F fantômes et un manquant est imputé au caissier.

Même effet en créant un mouvement sur la caisse d'un caissier dont le comptage est SOUMIS : le chiffre que le caissier a compté devient faux.

**Correctif** : dans `creer` et `annuler`, pour chaque compte `CAISSE_CAISSIER` touché, exiger une session OUVERTE (`exigerCaisseOuverte(compteId, dateMouvement, ...)`), comme pour les dépenses.

### M4. Justificatifs des dépenses et pièces des mouvements remplaçables après validation, sans trace, l'ancien fichier étant détruit

- **Gravité** : Moyenne. CVSS 4,3. OWASP A08 (intégrité des données) et A09.
- **Emplacement** :
  - `agence/depense/DepenseAgenceService.java:437-451` (`joindre`, puis `stockage.supprimer(ancien)`) ;
  - `agence/mouvement/MouvementService.java:199-213`.
- **Description** : n'importe quel profil avec `DEPENSE_SAISIR` (caissier compris) ou `MOUVEMENT_SAISIR` peut remplacer la pièce d'une dépense ou d'un mouvement **déjà validé ou annulé** de son agence, y compris celle d'un autre caissier. Le fichier d'origine est supprimé du disque et **aucune ligne d'audit** n'est écrite.
- **Scénario** : après l'approbation d'une dépense de 45 000 F sur la foi d'une facture, le caissier la remplace par un autre document. La preuve d'origine disparaît.
- **Correctif** :
  - n'autoriser le dépôt qu'au statut en attente, ou à l'auteur tant que rien n'est validé ;
  - au-delà, ajouter des pièces sans jamais supprimer ;
  - tracer `audit.tracer(..., "PIECE", ...)` avec l'empreinte SHA-256 de l'ancien et du nouveau fichier.

### M5. Commission modifiable sans borne, ce qui ouvre un dépassement d'entier sur les soldes

- **Gravité** : Moyenne. CVSS 4,3. OWASP A04.
- **Emplacement** :
  - `agence/operation/TransactionService.java:108` (`@PositiveOrZero Long commission`, sans maximum) ;
  - `:380-382` ;
  - `agence/tresorerie/RegistreService.java:169` et `:201` (`compte.getSolde() + montant` en `long`, sans `Math.addExact`).
- **Description** :
  - Avec `COMMISSION_MODIFIER` (superviseur par défaut, et caissier par la reprise E1), la commission peut valoir n'importe quel montant : 10 000 000 F sur une opération de 1 000 F, ou jusqu'à `Long.MAX_VALUE`.
  - Elle est créditée au compte `COMMISSIONS` ou au `FLOAT`, ce qui gonfle les revenus, la rentabilité et le float comptable.
  - Avec une valeur proche de `Long.MAX_VALUE`, `solde + montant` déborde **silencieusement** : le solde devient négatif, et les totaux des tableaux de bord et des clôtures sont faussés.
- **Correctif** :
  - borner la commission retenue, par exemple `≤ montant` ou `≤ k × commission proposée`, et en dur `@Max(1_000_000_000)` ;
  - utiliser `Math.addExact` dans `RegistreService` (refus en `BusinessException`) ;
  - poser `CHECK (solde BETWEEN -1e15 AND 1e15)` en base.

---

## FAIBLE

### F1. Rapprochement des commissions : crédit d'un montant libre sur n'importe quel compte, sans second visa

- **Emplacement** : `agence/commission/RapprochementService.java:76` (`@PositiveOrZero Long montantVerse`, sans borne) et `:150-215`.
- **Description** : il suffit de couvrir une commission en attente (même de 10 F) pour créditer `montantVerse` (même 50 000 000 F) sur un compte choisi, y compris la **caisse générale**. Seul `COMMISSION_RAPPROCHER` est requis. On obtient l'effet d'une régularisation positive sans validation par une autre personne. L'écart est conservé, mais rien ne le bloque.
- **Correctif** :
  - limiter le compte crédité aux familles `COMMISSIONS`, `BANQUE` et `FLOAT` du service ;
  - au-delà d'un écart réglable (par exemple ±10 % ou 50 000 F), mettre le versement en attente de validation, comme les mouvements.

### F2. Lot de cartes : dépassement d'entier `quantite × coutUnitaire`

- **Emplacement** : `agence/carte/CarteService.java:201-202` et `:264`.
- **Description** : `quantite` (`Integer`) et `coutUnitaire` (`Long` ≥ 0, sans maximum) sont multipliés en `long` sans contrôle. Exemple : `quantite = 4` et `coutUnitaire = 2^62 + 1` donnent un `total` qui retombe à 4 F. Le lot est alors « payé » 4 F alors que son coût unitaire affiché est gigantesque, ce qui fausse la valeur du stock, la marge et la rentabilité. Un `total` négatif saute même le débit (`total > 0`). Il faut `CARTE_STOCK` (superviseur).
- **Correctif** : `@Max` sur `coutUnitaire` et `quantite`, plus `Math.multiplyExact` et `Math.addExact`.

### F3. Lecture croisée entre caissiers d'une même agence (IDOR horizontal)

- **Emplacement** :
  - `DepenseAgenceService.detail` (`:467`) et `justificatif` : seul le périmètre de l'agence est contrôlé, alors que la liste est filtrée sur `auteurId` ;
  - `CarteService.lister` (`:325`) et `CarteController` `/ventes`, `/suivi`, `/stock` (`:68`, `:103`, `:109`) : sans `@PreAuthorize`, le caissier voit toutes les ventes de l'agence (nom et téléphone des clients), le chiffre d'affaires, la marge et les soldes de recharge, alors que `SOLDES_CONSULTER` lui est refusé ;
  - `TransactionService.clients` (`:307`) : le caissier recherche tout l'annuaire clients de l'entreprise, avec type et numéro de pièce d'identité.
- **Correctif** :
  - appliquer le même filtre « caissier seul, donc les siennes » dans `detail` et `justificatif` des dépenses, et dans les ventes de cartes ;
  - `@PreAuthorize("@droitsAgence.a('SOLDES_CONSULTER')")` sur `/suivi` ;
  - masquer le numéro de pièce dans `ClientDto` pour le caissier.

### F4. Plafond de dépense du caissier contournable par fractionnement ; doublons non détectés en hors ligne

- **Emplacement** :
  - `DepenseAgenceService.java:392` : plafond par opération, sans cumul journalier ;
  - `HorsLigneService.java:157` : `confirmerDoublon` est forcé à `true` pour toute synchronisation.
- **Description** :
  - Dix dépenses de 49 000 F passent sous un plafond de 50 000 F (sans validation quand le réglage est désactivé).
  - Une transaction rejouée sous une nouvelle clé hors ligne n'est jamais signalée comme doublon.
- **Correctif** :
  - plafond cumulé par session ou par jour ;
  - en hors ligne, transformer un doublon détecté en CONFLIT au lieu de le confirmer d'office.

### F5. Conditions de course résiduelles (à vérifier)

- **`SessionCaisseService.fermer` contre `TransactionService.enregistrer`.**
  - La transaction lit la session OUVERTE dans `preparer` avant de verrouiller le compte.
  - Si la fermeture est validée entre-temps, un dépôt est écrit dans la caisse d'une session FERMÉE, laissant un solde orphelin.
  - Correctif : verrouiller la ligne de session (`PESSIMISTIC_READ`) dans `enregistrer`, ou revérifier son statut après `registre.verrouiller`.
- **`ClotureService.valider` contre une écriture du jour.**
  - `VerrouJournee.exigerOuverte` est une simple lecture, sans verrou commun avec la validation.
  - Correctif : verrou consultatif `pg_advisory_xact_lock(agenceId)` dans les deux chemins.
- **`CarteService.numerosVerifies`.**
  - Le contrôle « carte déjà vendue » a lieu avant le verrou des lots, et aucune contrainte unique ne porte sur `agc_vente_carte_numero.numero`.
  - Deux ventes simultanées peuvent donc vendre le même numéro.

### F6. Immuabilité du journal et du registre uniquement applicative

- **Emplacement** : `V44__agences_multiservices.sql:259-270` ; base locale : rôle applicatif `parcauto` **superutilisateur** et propriétaire des tables (vérifié par `pg_roles` et `pg_tables`).
- **Description** :
  - Les déclencheurs ne couvrent ni `TRUNCATE`, ni `ALTER TABLE ... DISABLE TRIGGER`, ni `SET session_replication_role = replica`, tous accessibles au rôle de l'application.
  - Une injection SQL ailleurs, ou un accès aux identifiants de la base, suffit à réécrire le journal sans trace.
  - Il n'y a pas de chaînage par empreinte des lignes d'audit.
- **Correctif** :
  - en production, un rôle applicatif non propriétaire et non superutilisateur (`GRANT INSERT, SELECT` seulement sur `agc_journal_audit`, `agc_ecriture` et `agc_ligne_registre`) ;
  - un déclencheur `BEFORE TRUNCATE` ;
  - optionnellement, une colonne `empreinte = sha256(empreinte_precedente || contenu)`.

### F7. File hors ligne et copies en clair dans `localStorage`, conservées après déconnexion

- **Emplacement** : `frontend/src/app/core/services/agences-hors-ligne.service.ts:206-247`.
- **Description** : les opérations en attente (nom, téléphone, type et numéro de pièce des clients, montants) et les copies de référence restent dans `localStorage` sous `agences.horsligne.*` et `agences.copie.*`, sans chiffrement ni purge à la déconnexion. Sur une tablette d'agence partagée, ces données restent lisibles.
- **Correctif** : purger les copies à la déconnexion (garder seulement la file non envoyée), et y minimiser les données d'identité.

---

## INFO

- **I1.** La clé `X-Cle-Operation` est unique par entreprise et non par utilisateur. Une clé déjà utilisée par un autre caissier renvoie « Opération déjà reçue : <référence> », une fuite mineure. Correctif : préfixer la clé par `auteurId` côté serveur.
- **I2.** Un conflit hors ligne sans `agenceId` échappe au contrôle de périmètre de `conflit()` (`HorsLigneService.java:282`) et n'apparaît dans aucune liste. Une reprise par identifiant reste possible pour tout profil `CAISSE_GERER` de l'entreprise. Correctif : refuser un `agenceId` absent dès la réception.
- **I3.** Les exports CSV des rapports agences (`RapportAgenceController` vers `pointdevente/rapport/ExportRapport`) réutilisent la neutralisation déjà signalée comme contournable (« -1+… »). Ils contiennent des textes libres saisis par les caissiers (nom du client, observations). Ce n'est pas un nouveau constat : le correctif commun couvre aussi les agences.
- **I4.** `ReglageAgenceService.Reglages.montantMaxTransaction` (`@Positive`, sans maximum) et `SoldeService.RequeteCompte.decouvertAutorise` (sans maximum) sont réservés à l'administrateur. Les borner pour rester cohérent avec M5.

---

## Synthèse

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 2 (E1, E2) |
| Moyenne | 5 (M1 à M5) |
| Faible | 7 (F1 à F7) |
| Info | 4 |

**Les 3 priorités** :
1. **E1 et E2 (hors ligne)** :
   - `@ModuleRequis` et `@PreAuthorize`, avec bornes de taille, sur la synchronisation ;
   - reprise exécutée avec les droits de l'auteur d'origine ;
   - résumé construit côté serveur et détail réel montré avant reprise.
2. **M1 et M2 (sortie d'argent sans second regard)** : validation des dépenses quel que soit le compte ; interdiction au superviseur de fermer ou régulariser sa propre caisse ; seuil d'écart soumis à validation.
3. **M3, M4 et M5 (intégrité comptable)** : règle « caisse ouverte » appliquée aux mouvements ; pièces justificatives figées après validation et tracées ; commission bornée et arithmétique `addExact` dans le registre.

**État des surfaces** :

| Surface | État |
|---|---|
| Authentification | Correcte (401 partout sans jeton). Les questions de jeton et de limitation relèvent du socle déjà audité. |
| Autorisation et cloisonnement | Bon entre entreprises et entre agences. Faiblesses : profil et module contournés sur la synchronisation hors ligne (E2), reprise en « confused deputy » (E1), lectures croisées entre caissiers (F3). |
| Argent et caisses | Registre solide (verrous, découvert, annulation unique, journées clôturées). Faiblesses de séparation des tâches et de contournement de validation (M1, M2, F1), de cohérence après fermeture (M3) et d'arithmétique non bornée (M5, F2). |
| Points publics | Le module n'en expose aucun. |
| Injection | Aucune injection SQL ou JPQL trouvée : Criteria API, paramètres liés et compteur natif paramétré. Aucun puits XSS côté front agences. CSV : constat commun déjà connu (I3). |
| Fichiers | Liste blanche d'extensions et de types, `nosniff`, logos en data URL limités à JPEG, PNG et WebP. Seule réserve : remplacement destructif des pièces (M4). |
| Journal d'audit | Immuable au niveau applicatif. Affaibli par le rôle superutilisateur de la base (F6) et polluable par E2. |
| Infra (propre au module) | Rôle base superutilisateur et propriétaire (F6), à corriger pour la production. |

**Confirmé ou à vérifier** :
- Tous les constats E et M, ainsi que F1, F2, F3, F4, F6 et F7, sont confirmés par lecture du code. F6 est aussi confirmé par lecture de la base.
- F5 est **à vérifier** (conditions de course déduites du code, non reproduites).
- Aucun test dynamique authentifié n'a été fait : la connexion écrit dans `journal_activite`, ce que la consigne interdit. Pour confirmer E1 et E2 en dynamique, il faudrait l'accord du propriétaire pour se connecter avec `caisse.agence@test.local` et `sup.agence@test.local` sur 8085, ce qui crée des lignes de connexion, de conflit et d'audit.
