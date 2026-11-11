# Audit sécurité — Module 2/13 : PLATEFORME (console super-admin, abonnements SaaS, inscription, journal général)

Date : 2026-11-11 — branche `saas` (dernier commit 4698cb4) — API de test http://localhost:8085 (base `parcauto_saas`).
Périmètre : `backend/src/main/java/tg/parcauto/api/plateforme/**` (entreprise, abonnement, module, inscription, journal, tenant), son branchement (`config/WebMvcConfig`, `security/JwtAuthenticationFilter`, `config/AdminInitializer`) et les écrans Angular `/plateforme/**`, `/abonnement`, `/modules`, `/inscription`.
Constats du socle déjà connus et non repris : clé JWT de dev, pas de limite sur `/api/auth/login`, jeton en localStorage, X-Forwarded-For non contrôlé, Swagger public, Spring Boot 3.4.1, envois de 60 Mo.

## Méthode et preuves dynamiques

Aucune donnée n'a été écrite. Les jetons de test ont été fabriqués localement avec la clé de dev (constat socle déjà connu), à partir de `uid`/`version_jetons` lus en SELECT dans `parcauto_saas` — pas de connexion, donc aucune ligne ajoutée au journal. Seule requête POST : une inscription avec une adresse **existante**, refusée avant toute écriture.

| Requête (8085) | Jeton | Résultat |
|---|---|---|
| GET `/api/plateforme/{entreprises,statistiques,tableau-bord,abonnements,abonnements/paiements,abonnements/parametres,journal}` | admin d'entreprise (agences@test.local, ADMIN) | **403** partout |
| mêmes routes | aucun | **401** partout |
| GET `/api/plateforme/entreprises` | jeton admin dont la claim `roles` est forcée à `SUPER_ADMIN` et sans `eid` | **403** (les rôles sont relus en base) |
| GET `/api/abonnement`, `/api/abonnement/paiements`, `/api/abonnement/offre`, `/api/modules` | caissier d'agence | **403** |
| GET `/api/abonnement/etat` | admin | 200 (état ESSAI) |
| POST `/api/public/inscription` avec `QUINCAILLERIE@test.local` | aucun | **409** « Cette adresse ne peut pas servir a une nouvelle inscription… » (voir M-2) |

**Ce qui tient (confirmé par le code et les tests)** :
- Contrôleurs `/api/plateforme/**` tous annotés `@PreAuthorize(hasRole('SUPER_ADMIN'))` au niveau de la classe.
- Rôle et entreprise relus en base à chaque requête (`JwtAuthenticationFilter.java:40-56`) : la suspension prend effet aussitôt pour les comptes connectés.
- Le rôle SUPER_ADMIN ne peut pas être attribué par une entreprise (`UtilisateurService.java:181,231`).
- Aucun IDOR sur les paiements d'abonnement : `paiementDe` compare l'entreprise (`AbonnementService.java:726-732`).
- Montant déclaré recalculé côté serveur à partir du devis (`AbonnementService.java:419-428`). Validation double protégée par `@Version`.
- Modules : après paiement, seuls les modules payés s'activent (`ModuleController.java:51`, `AbonnementService.java:586-594`) ; `mettreEnVigueur` désactive les autres.
- Journal général en SQL paramétré, `LIKE` échappé, taille de page bornée à 100 (`JournalPlateformeService.java:143-150,247-287`).
- Preuves de paiement :
  - type tiré de l'extension (PDF, PNG, JPG ou WEBP seulement) ;
  - nom de stockage UUID, sans traversée de chemin (`StockageFichierService.java:43-55`) ;
  - en-tête `Content-Disposition` nettoyé et `nosniff` présent.
- Angular :
  - aucun `[innerHTML]` ni `bypassSecurityTrust*` dans les écrans plateforme et abonnement ;
  - routes `/plateforme/**` protégées par `roleGuard('SUPER_ADMIN')`, le serveur restant l'autorité.

---

## Critique

Aucun constat.

## Élevée

Aucun constat.

## Moyenne

### M-1 — Lecture seule et suspension contournées par les points publics à jeton (QR restaurant, pointage de présence, bornes, GPS) — **confirmé (code)**
- **Gravité** : Moyenne — CVSS 3.1 indicatif 5.3 (AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:L/A:N). OWASP A01 (contrôle d'accès défaillant) et A04 (logique métier).
- **Emplacement** :
  - `config/WebMvcConfig.java:36-38` : `/api/public/**` est exclu de `LectureSeuleInterceptor`.
  - `gps/ContexteGpsInterceptor.java:31-48` et `gps/JetonGpsService.java:55-66` : aucun contrôle du statut de l'entreprise ni de la lecture seule.
  - `presence/ContextePresenceInterceptor.java:50-63` : idem.
  - `pointdevente/restauration/qr/ContexteTableInterceptor.java:53-58` : idem.
  - À l'inverse, `marketplace/ContexteBoutiqueInterceptor.java:55-66` contrôle bien `e.statut = 'ACTIVE'` et `abonnements.lectureSeule(...)` hors GET.
- **Description** :
  - Une entreprise **suspendue** par le super-admin (`PATCH /api/plateforme/entreprises/{id}/statut`) ou dont l'abonnement est **expiré** (lecture seule) continue d'écrire par les points suivants :
    - `POST /api/public/qr/{jeton}/commande` et `/appel` (commandes de table du restaurant) ;
    - `POST /api/public/presence/lieux/{jeton}/pointage`, `/historique`, et `/bornes/{jeton}/pointage` ;
    - `POST /api/public/gps/positions/{fournisseur}` (positions des traceurs).
  - Le module Présence fonctionne surtout par ces pointages publics : une entreprise qui ne paie plus garde donc l'usage principal du module. Le restaurant continue aussi de prendre des commandes à table, et le GPS continue d'enregistrer.
- **Scénario** : un client laisse expirer son abonnement Présence + GPS. Ses bornes et ses traceurs continuent d'enregistrer pointages et positions. Il consulte ensuite tout en lecture (les GET restent ouverts) : il garde le service sans payer. Même chose après une suspension pour fraude.
- **Correctif** :
  - Factoriser dans un seul composant le contrôle que fait déjà `ContexteBoutiqueInterceptor`, puis l'appeler depuis les quatre intercepteurs de contexte public :
    ```java
    // après avoir trouvé l'entreprise du jeton
    if (!entrepriseActive(entreprise)) throw new ResourceNotFoundException("Service indisponible");
    if (!"GET".equalsIgnoreCase(request.getMethod()) && abonnements.lectureSeule(entreprise))
        throw new AbonnementExpireException();
    ```
  - Ou poser ce contrôle dans `LectureSeuleInterceptor` lui-même, quand `ContexteEntreprise.courante()` est renseigné, sans exclure `/api/public/**` (garder seulement `/api/public/inscription` et `/api/public/modules`). Cela suppose que l'intercepteur s'exécute après les intercepteurs de contexte (order 2 > 0, c'est déjà le cas).

### M-2 — Inscription publique : énumération des comptes, aucune vérification d'adresse, essais gratuits renouvelables — **confirmé (test + code)**
- **Gravité** : Moyenne — CVSS indicatif 5.3 (AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:L/A:N). OWASP A07 (identification) et A04.
- **Emplacement** :
  - `plateforme/inscription/InscriptionService.java:79-83` : message distinct si l'adresse existe ;
  - `InscriptionService.java:95-104` : compte actif sans vérification d'adresse ;
  - `InscriptionController.java:47-49` : 5 inscriptions par heure et par IP seulement ;
  - `AbonnementService.java:283-295` : nouvel essai à chaque nouvelle entreprise.
- **Description et preuve** :
  - `POST /api/public/inscription` avec une adresse existante renvoie **409** et le message « Cette adresse ne peut pas servir a une nouvelle inscription. Si vous avez deja un compte, connectez-vous… ». Une adresse libre crée le compte (201). C'est un oracle d'existence des comptes, **super-admin compris** : son adresse par défaut est `superadmin@plateforme.local` (`application.yml:51`).
  - Aucune preuve de possession de l'adresse : n'importe qui peut réserver l'adresse d'un tiers (qui ne pourra plus s'inscrire) ou ouvrir des entreprises en série.
  - Chaque inscription ouvre un essai de 14 jours avec **tous les modules et sans aucune limite de volume** (limites ignorées pendant l'essai, `AbonnementService.java:572-573`). Avec une nouvelle adresse tous les 14 jours, on enchaîne les essais. La limite par IP se contourne en changeant d'IP, et `getRemoteAddr` derrière un mandataire est un point déjà connu.
- **Scénario** :
  1. L'attaquant confirme que `superadmin@plateforme.local` existe (409).
  2. Il attaque ce compte par force brute sur `/api/auth/login`, qui n'a pas de limite (constat socle) : la console entière est exposée.
  3. Autre usage : un concurrent réserve les adresses d'entreprises clientes, ou une entreprise utilise la plateforme indéfiniment par essais successifs.
- **Correctif** :
  1. Réponse identique (202) que l'adresse existe ou non. Le compte n'est créé et la session ouverte qu'après un clic sur un lien envoyé à l'adresse (jeton à usage unique, haché en base, valable 24 h). Si l'adresse existe déjà, envoyer plutôt un courriel « vous avez déjà un compte ».
  2. Captcha (Turnstile ou hCaptcha) sur l'inscription.
  3. Limites d'essai : un essai par numéro de téléphone ou domaine vérifié, ou des limites de volume pendant l'essai (créer une formule « Essai » avec `maxUtilisateurs` et `maxVehicules`).
  4. En production, définir `PLATEFORME_ADMIN_EMAIL` avec une adresse non devinable (la refuser par défaut dans `GardeSecrets` comme le mot de passe).

## Faible

### F-1 — Entreprise créée par le super-admin : son mot de passe n'est pas provisoire — **confirmé (code)**
- **Gravité** : Faible — OWASP A07.
- **Emplacement** :
  - `plateforme/entreprise/PlateformeService.java:52-58` ;
  - `plateforme/inscription/InscriptionService.java:95-103` : `setMotDePasseAChanger` n'est jamais posé.
- **Description** : `POST /api/plateforme/entreprises` crée l'administrateur de l'entreprise avec le mot de passe saisi par le super-admin. Ce mot de passe n'est jamais marqué « à changer » : l'exploitant (ou quiconque l'a vu transmis) connaît durablement le mot de passe de l'administrateur client. Cela casse la non-répudiation des actions journalisées et la séparation plateforme / entreprise.
- **Correctif** : dans `InscriptionService.creer`, ajouter `admin.setMotDePasseAChanger(joursEssai != null || creeParPlateforme)`. Mieux : passer un indicateur explicite `creeParPlateforme`, puis générer un mot de passe aléatoire ou envoyer un lien d'activation.

### F-2 — Plusieurs déclarations de paiement « en attente » possibles en concurrence ; aucun contrôle des références réutilisées — **confirmé (code)**
- **Gravité** : Faible — OWASP A04.
- **Emplacement** :
  - `AbonnementService.java:419-428` : vérification puis insertion, sans verrou ;
  - `db/migration/V68__abonnements_saas.sql:123-128` : aucun index unique partiel.
- **Description** :
  - `existsByEntrepriseIdAndStatut(EN_ATTENTE)` puis `save` : deux POST simultanés créent deux déclarations en attente. Valider les deux empile deux périodes pour un seul virement réel si l'exploitant ne recoupe pas.
  - Rien n'empêche non plus de déclarer **la même référence** mobile money ou bancaire pour deux paiements ou deux entreprises. La validation, manuelle, ne signale pas le doublon.
- **Correctif** :
  ```sql
  CREATE UNIQUE INDEX ux_abo_paiement_attente ON abo_paiement (entreprise_id) WHERE statut = 'EN_ATTENTE';
  CREATE UNIQUE INDEX ux_abo_paiement_reference ON abo_paiement (moyen, lower(reference))
      WHERE reference IS NOT NULL AND statut <> 'REJETE';
  ```
  Traduire la violation en `BusinessException`. Dans la console, afficher un avertissement « référence déjà vue » au moment de valider.

### F-3 — Limites de formule (utilisateurs, véhicules, agences, caisses, employés) contournables par requêtes simultanées — **à vérifier (code)**
- **Gravité** : Faible — OWASP A04.
- **Emplacement** : `AbonnementService.java:567-582` (`verifierLimite` : comptage puis insertion par l'appelant), appelé depuis :
  - `CamionService.java:80` ;
  - `UtilisateurService.java:56` ;
  - `AgenceService.java:89` ;
  - `GestionCaisseService.java:82` ;
  - `PresenceService.java:125`.
- **Description** : contrôle « vérifier puis agir » sans verrou. N requêtes POST parallèles au moment où il reste une place créent N éléments au-delà de la limite payée.
- **Correctif** : dans `verifierLimite`, poser un verrou par entreprise et par ressource : `SELECT pg_advisory_xact_lock(:entreprise, :ressource)` avant le comptage, dans la même transaction que l'insertion. Ou verrouiller la ligne `abo_abonnement` (`SELECT … FOR UPDATE`).

### F-4 — Un module sans prix dans une formule est facturé 0 — **confirmé (code), sans impact sur les données actuelles**
- **Gravité** : Faible — OWASP A04.
- **Emplacement** :
  - `abonnement/Formule.java:74-76` : `prix.getOrDefault(module, BigDecimal.ZERO)` ;
  - `AbonnementService.java:395-414` : le devis accepte une ligne à 0.
- **Description** : quand un nouveau module devient `disponible`, toute formule qui ne le tarife pas encore le propose à 0 FCFA. Une entreprise peut alors déclarer ce module gratuitement (le montant peut même être 0). Si l'exploitant valide par habitude, le module devient payé. Aujourd'hui les 3 formules tarifent les 5 modules.
- **Correctif** : dans `devis(...)`, refuser un module sans prix explicite (`!f.getPrix().containsKey(m)` → « module non proposé dans cette formule »). Ne pas l'afficher dans `vue(Formule)` ; refuser aussi une déclaration dont le montant est nul.

### F-5 — Console super-admin protégée seulement par `@PreAuthorize`, sans règle d'URL ni second facteur — **confirmé (code)**
- **Gravité** : Faible (défense en profondeur) — OWASP A01 et A07.
- **Emplacement** :
  - `config/SecurityConfig.java:54-56` : seulement `permitAll` pour PUBLIC, puis `anyRequest().authenticated()` ;
  - `security/AuthController.java:55-76` : connexion par mot de passe seul.
- **Description** :
  - Un futur contrôleur ajouté sous `/api/plateforme/...` sans annotation serait ouvert à tout compte connecté.
  - Le compte le plus privilégié se connecte avec un seul facteur : il peut suspendre toutes les entreprises, prolonger ou valider des abonnements, et lire le journal de toutes les entreprises avec les valeurs avant/après.
- **Correctif** :
  - Ajouter `.requestMatchers("/api/plateforme/**").hasRole("SUPER_ADMIN")` avant `anyRequest()`.
  - Prévoir une TOTP obligatoire pour SUPER_ADMIN.
  - Envisager une liste d'IP autorisées ou un hôte d'administration distinct.

## Info / robustesse

- **I-1** — `POST /api/plateforme/abonnements/paiements/{id}/rejet` sans corps : `rejet` vaut `null`, ce qui provoque une NullPointerException et une erreur 500 (`AbonnementPlateformeController.java:118-121`). Ajouter `@Valid @RequestBody` et `@NotBlank` sur `motif`.
- **I-2** — `joindrePreuve` stocke le nom d'origine entier dans `preuve_nom` (200 caractères au plus) : un nom plus long provoque une erreur 500. L'ancienne preuve est supprimée du disque avant la validation de la transaction (`AbonnementService.java:495-506`). Tronquer avec `Path.getFileName()` ; supprimer l'ancien fichier après validation (`TransactionSynchronization.afterCommit`).
- **I-3** — Pendant l'essai et sa grâce, l'administrateur active n'importe quel module, et aucune limite de volume ne s'applique : c'est un choix produit. Le signaler à cause de M-2, puisque l'essai est renouvelable.
- **I-4** — Le journal général expose au super-admin les valeurs avant/après des données métier de toutes les entreprises. C'est voulu, mais cela fait du compte SUPER_ADMIN une cible de grande valeur (voir F-5).
- **I-5** — Les noms d'entreprise et d'auteur ainsi que les descriptions, contrôlés par les entreprises, sont affichés dans la console par interpolation Angular : pas de XSS constaté. Aucun export CSV ou Excel du journal général, donc pas d'injection de formules à ce niveau.

---

## Synthèse

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 0 |
| Moyenne | 2 |
| Faible | 5 |
| Info | 5 |

**3 priorités**
1. **M-1** : appliquer suspension et lecture seule aux points publics à jeton (QR restaurant, présence, bornes, GPS), comme pour la boutique.
2. **M-2** : inscription avec réponse neutre, vérification de l'adresse, captcha et encadrement des essais ; adresse du super-admin non devinable en production.
3. **F-5 et F-1** : règle d'URL `/api/plateforme/**` et TOTP pour le super-admin ; mot de passe provisoire pour les entreprises créées par l'exploitant.

**État par surface**
- **Authentification** : correcte pour ce module (rôles relus en base, falsification de claims sans effet). Reste l'énumération par l'inscription et l'absence de second facteur pour le super-admin.
- **Autorisation et cloisonnement** : solide. Toutes les routes plateforme refusent l'admin d'entreprise (403 vérifié), aucun IDOR sur les paiements et preuves, rôle SUPER_ADMIN non attribuable.
- **Argent et abonnements** : montants recalculés côté serveur, validation à usage unique (`@Version`), modules alignés sur ceux payés. Points faibles : concurrence des déclarations, références non dédoublonnées, prix à 0 par défaut, limites contournables par concurrence.
- **Points publics** : la lecture seule et la suspension ne s'appliquent qu'à la boutique ; GPS, présence et QR restaurant continuent d'écrire (M-1).
- **Injection** : rien de trouvé. SQL paramétré, `LIKE` échappé ; les seules concaténations portent sur des constantes (`Ressource`, `TableauBordPlateformeService.java:216-217` avec une date générée par le serveur).
- **Infra et configuration** : adresse du super-admin par défaut devinable (`application.yml:51`). Le reste relève du socle déjà audité.
