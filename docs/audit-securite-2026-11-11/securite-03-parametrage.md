# Audit sécurité LJ SAAS — module 3/13 : PARAMÉTRAGE

Date : 2026-11-11 — branche `saas` — périmètre : `tg.parcauto.api.parametrage` (identité de l'entreprise, logo, format, TVA), comptes et profils (`security/UtilisateurController`, `UtilisateurService`, `UtilisateurDto`, `PinController`/`PinService`), activation des modules (`plateforme/module/ModuleController`), réglages des modules (`pointdevente/reglage`, droits des agences `agence/DroitsAgence` + `ReglageAgenceService.enregistrerDroits`), écrans Angular `pages/parametrage`, `pages/utilisateurs`, `core/services/verrou.service.ts`.

Déjà couvert ailleurs et non repris : clé JWT de dev, absence de limite sur `/api/auth/login`, jeton en localStorage, X-Forwarded-For, Swagger public, Spring Boot 3.4.1, envois de 60 Mo, points publics GPS/présence/QR, inscription qui révèle les adresses, règle d'URL `/api/plateforme/**`.

## Méthode et limites

- Revue statique complète des fichiers ci-dessus.
- Tests dynamiques **en lecture seule** sur `http://localhost:8085/api`. Pour ne rien écrire (une connexion écrit une ligne `CONNEXION` dans le journal d'audit), les jetons des comptes de test (`agences@test.local` ADMIN id 32, `sup.agence@test.local` id 33, `caisse.agence@test.local` id 34, entreprise 15) ont été signés localement avec la clé de dev par défaut (constat déjà connu). Les requêtes PUT/POST/DELETE ont été **refusées par la politique d'exécution** : les comportements en écriture sont donc « confirmés par le code » et non par une requête.
- Requêtes SQL en lecture seule sur `parcauto_saas` (structure du journal, index).

---

## Synthèse des gravités

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 0 |
| Moyenne | 3 |
| Faible | 4 |
| Info | 7 |

Aucune élévation de privilèges ni aucun IDOR entre entreprises n'a été trouvé sur les comptes ou le paramétrage : les contrôles principaux tiennent (voir « Points vérifiés conformes »).

---

## MOYENNE

### M1 — Les changements de profils (rôles) ne sont jamais journalisés
- **Gravité** : Moyenne — CVSS 3.1 indicatif 4.3 (AV:N/AC:L/PR:H/UI:N/S:U/C:N/I:L/A:N, aggravé par l'impact sur l'enquête) — OWASP A09:2021 Security Logging and Monitoring Failures.
- **Statut** : confirmé (code + base).
- **Emplacement** :
  - `backend/src/main/java/tg/parcauto/api/common/audit/EcouteurAudit.java:155` (mise à jour : `estCollection(...)` → champ ignoré) et `:279-281`, `:290-291` (création : collections ignorées) ;
  - `backend/src/main/java/tg/parcauto/api/security/Utilisateur.java:176-180` (`roles` = `@ElementCollection`) ;
  - `backend/src/main/java/tg/parcauto/api/security/UtilisateurService.java:66`, `:99` (`setRoles`).
- **Description** : l'écouteur d'audit écarte toute propriété de type collection. Or les profils d'un compte sont une `@ElementCollection`. Quand un administrateur ajoute le profil ADMIN à un compte (ou retire celui d'un autre administrateur), seuls `version` et `modifieLe` changent sur l'entité, tous deux dans `CHAMPS_IGNORES` : `apres.isEmpty()` → aucune ligne n'est écrite (`EcouteurAudit.java:165-167`). À la création, la ligne ne mentionne pas non plus les profils accordés.
- **Preuve** :
  ```java
  // EcouteurAudit.java:155
  if (CHAMPS_IGNORES.contains(noms[i]) || estCollection(nouveau[i]) || estCollection(ancien[i])) {
      continue;
  }
  ```
  Requête SQL en lecture : `select count(*) from journal_activite where objet='Utilisateur' and (avant ilike '%roles%' or apres ilike '%roles%' or description ilike '%profil%')` → **0**. Les seules entrées `Utilisateur` sont CONNEXION, ECHEC_CONNEXION et un changement de `pin hash`.
- **Scénario** : un administrateur malveillant (ou un jeton administrateur volé) promeut un complice ADMIN ou CAISSIER pour quelques heures, détourne des fonds, puis lui retire le profil. Le journal d'audit, censé être immuable et complet, ne garde aucune trace de l'octroi ni du retrait.
- **Correctif** :
  - tracer explicitement dans `UtilisateurService.creer/modifier` une action `PROFILS` avec avant/après (`journal.tracer(..., "MODIFICATION", "Utilisateur", id, "Profils : GERANT → ADMIN, GERANT", avant, apres)`) ;
  - ou, de façon générique, enregistrer un `PostCollectionUpdateEventListener` / `PostCollectionRecreateEventListener` dans `EcouteurAudit` pour les collections de valeurs simples (rôles, `utilisateurIds` des agences…) ;
  - tracer aussi `definirPin` (effacement), `reinitialiserMotDePasse` et `changerActivation` avec un libellé clair (l'auteur et la cible).

### M2 — Le mot de passe fixé par l'administrateur n'est jamais à changer : l'administrateur peut agir au nom d'un caissier
- **Gravité** : Moyenne — CVSS indicatif 5.0 (AV:N/AC:L/PR:H/UI:N/S:U/C:L/I:H/A:N) — OWASP A07:2021 Identification and Authentication Failures ; ASVS 2.5.x (secrets initiaux et réinitialisation).
- **Statut** : confirmé par le code (écriture non testée : refusée par la politique d'exécution).
- **Emplacement** :
  - `backend/src/main/java/tg/parcauto/api/security/UtilisateurService.java:67-68` (création : `motDePasseAChanger` reste `false`) ;
  - `UtilisateurService.java:127-132` (réinitialisation : idem) ;
  - le mécanisme existe pourtant : `JwtAuthenticationFilter.java:48-51`, `UtilisateurService.java:164`.
- **Description** : à la création d'un compte ou à la réinitialisation par l'administrateur, le mot de passe saisi par l'administrateur devient définitif. L'utilisateur n'est jamais obligé de le changer, et l'administrateur le connaît.
- **Scénario** : un administrateur (ou un gérant qui a obtenu le profil ADMIN) réinitialise le mot de passe d'un caissier, se connecte avec, encaisse ou annule des ventes, clôture la caisse. Le journal attribue ces actes au caissier. La non-répudiation des caisses (point central du produit) est perdue. Variante : un administrateur crée des comptes avec un mot de passe commun « Caisse2026! » que personne ne change.
- **Correctif** :
  ```java
  // creer(...) et reinitialiserMotDePasse(...)
  utilisateur.setMotDePasseAChanger(true);
  ```
  Le filtre JWT bloque déjà tout sauf `/api/auth/**` et `/api/utilisateurs/moi/**` tant que le mot de passe n'est pas changé. En complément : journaliser la réinitialisation (voir M1) et prévenir le titulaire (notification ou e-mail).

### M3 — Verrou PIN de caisse : contrôle côté client seulement, empreinte du PIN en localStorage, limite d'essais contournable hors ligne
- **Gravité** : Moyenne — CVSS indicatif 4.6 (AV:P/AC:L/PR:N/UI:N/S:U/C:H/I:L/A:N) — OWASP A07:2021 ; ASVS 2.4 (stockage des secrets) et 2.2.1 (anti-automatisation).
- **Statut** : confirmé par le code.
- **Emplacement** :
  - `frontend/src/app/core/services/verrou.service.ts:42-62` (repli hors ligne dès que `status === 0`) ;
  - `verrou.service.ts:66-69` (empreinte = SHA-256 d'un sel prévisible `lj-saas:<id>:<email>:` + PIN) ;
  - `verrou.service.ts:71-75` (empreinte écrite dans `localStorage`) ;
  - `backend/src/main/java/tg/parcauto/api/security/PinController.java:173-178` / `PinService.java:68-95` (la vérification ne change aucun état côté serveur : le JWT reste pleinement valide pendant le verrouillage).
- **Description** :
  1. Le verrou de caisse n'existe que dans le navigateur : la vérification renvoie 204 et rien d'autre. Le jeton de session, lui aussi dans `localStorage`, reste utilisable pendant le verrouillage.
  2. Le PIN (4 à 6 chiffres) est stocké sous forme de SHA-256 rapide, avec un sel connu (identifiant et e-mail). Les 10^6 combinaisons se testent en moins d'une seconde.
  3. Couper le réseau (mode hors ligne des outils du navigateur) fait passer `verifier()` dans la branche locale, qui n'a **aucun** compteur d'essais : la limite de 5 essais / 15 min du serveur (`PinService.ESSAIS_MAX`) ne s'applique plus.
- **Scénario** : sur un poste de caisse partagé et verrouillé, un collègue ouvre les outils du navigateur, lit `pin.empreinte.<id>` et trouve le PIN hors ligne en une seconde, ou passe en mode hors ligne et essaie les codes sans limite. Il déverrouille la caisse du titulaire, et peut réutiliser ce PIN plus tard sur ce poste.
- **Correctif** :
  - Côté serveur, faire du verrou un état réel : à l'expiration de l'inactivité, le client demande `/api/auth/pin/verrou`, qui pose un drapeau `verrouille` sur la session (ou incrémente une « version de déverrouillage » portée par un jeton court). Les endpoints de caisse (`PDV_CAISSE`, `TRANSACTION_SAISIR`…) refusent tant que la session n'a pas été déverrouillée par `/verification`.
  - Hors ligne : dériver l'empreinte avec PBKDF2 (WebCrypto `deriveBits`, ≥ 600 000 itérations) et un sel aléatoire par appareil ; imposer localement le même compteur (5 essais puis blocage, persistant) ; exiger 6 chiffres pour les PIN de caisse.
  - À défaut, documenter que le verrou est une simple protection d'écran, et non un contrôle d'accès.

---

## FAIBLE

### F1 — Énumération des adresses de toutes les entreprises (et du super-admin) via la gestion des comptes
- **Gravité** : Faible — CVSS 3.1 indicatif 2.7 (PR:H, C:L) — OWASP A01/A04 (fuite d'information inter-locataires).
- **Statut** : confirmé par le code (le POST/PUT n'a pas pu être joué).
- **Emplacement** : `UtilisateurService.java:57-59` (`existsByEmailIgnoreCase` global) et `:75-79` (`findByEmailIgnoreCase` global), message « Un compte existe deja avec l'adresse … ».
- **Description** : la table `utilisateur` n'est pas cloisonnée et l'unicité est globale (index `ux_utilisateur_email_minuscules`). Un administrateur d'entreprise peut appeler en boucle `PUT /api/utilisateurs/{sonPropreCompteNonAdmin}` avec des adresses candidates : la réponse 400 « existe déjà » révèle qu'un compte existe dans **une autre** entreprise, y compris le super-admin. L'appel PUT n'écrit rien quand l'adresse existe ; s'il n'existe pas, il modifie l'adresse (réversible). Le POST est freiné par la limite d'utilisateurs de l'abonnement.
- **Scénario** : un concurrent abonné repère quels de ses prospects sont clients de LJ SAAS, puis repère l'adresse du super-admin (cible du bourrage d'identifiants, sans limite sur le login).
- **Correctif** : un message générique (« Cette adresse ne peut pas être utilisée »), un limiteur (`LimiteurRequetes`) sur ces endpoints, et une journalisation des refus. Le constat recoupe celui de l'inscription : le corriger au même endroit.

### F2 — Logo : type déclaré par le navigateur, pas de contrôle du contenu ni de ré-encodage ; métadonnées publiées
- **Gravité** : Faible — CVSS indicatif 3.1 — OWASP A04/A05 ; ASVS 12.2.1.
- **Statut** : confirmé par le code.
- **Emplacement** : `backend/src/main/java/tg/parcauto/api/parametrage/SocieteService.java:83-102` ; exposition publique `marketplace/BoutiqueService.java:94-96` → `BoutiquePubliqueController.java:46`.
- **Description** : seul `fichier.getContentType()` (fourni par le client) est contrôlé. N'importe quels octets ≤ 512 Ko sont acceptés sous `image/png`. Points positifs : le SVG est refusé, aucun chemin disque n'est utilisé, et l'affichage passe par `<img src="data:image/...">`, donc il n'y a ni XSS ni traversée. Restent :
  - (a) des octets malformés font échouer `Image.getInstance` dans tous les PDF de l'entreprise (factures, rapports, tickets), un déni de service limité à l'entreprise ;
  - (b) les métadonnées EXIF (GPS, appareil, auteur) d'une photo sont republiées telles quelles dans la boutique publique.
- **Correctif** : vérifier les octets magiques, puis décoder et ré-encoder l'image (`ImageIO.read` → `ImageIO.write("png")`), ce qui supprime aussi l'EXIF. Refuser si le décodage échoue, borner les dimensions (par exemple 2000×2000) et déduire `logoType` du format détecté, pas du client.

### F3 — Changement de son mot de passe sans limite d'essais sur l'ancien mot de passe
- **Gravité** : Faible — CVSS indicatif 3.7 — OWASP A07 ; ASVS 2.2.1.
- **Statut** : confirmé par le code.
- **Emplacement** : `security/UtilisateurController.java:233-240`, `UtilisateurService.java:154-167`.
- **Description** : `PUT /api/utilisateurs/moi/mot-de-passe` compare `motDePasseActuel` sans compteur ni délai. Un jeton volé (XSS, poste partagé) permet de deviner l'ancien mot de passe à volonté. Une fois trouvé, le mot de passe est changé, les autres sessions sont fermées et le titulaire perd son compte de façon persistante. L'absence de limite sur `/api/auth/login` est déjà connue ; ce point est un second chemin, à corriger en même temps.
- **Correctif** : appliquer `LimiteurRequetes` par utilisateur (par exemple 5 échecs / 15 min, comme le PIN), journaliser les échecs (`ECHEC_CHANGEMENT_MDP`) et fermer la session après N échecs.

### F4 — Opérations sensibles d'administration sans ré-authentification
- **Gravité** : Faible — CVSS indicatif 3.8 — ASVS 2.x / 3.x (step-up pour opérations sensibles).
- **Statut** : confirmé par le code.
- **Emplacement** : `UtilisateurController.java:176-218` (création avec profil ADMIN, changement d'adresse, réinitialisation de mot de passe, PIN) ; `ModuleController.java:45-73`.
- **Description** : un jeton administrateur, qui reste dans `localStorage` pendant 8 h, suffit pour plusieurs actions sans ressaisir le mot de passe :
  - créer un administrateur de secours ;
  - changer l'adresse puis le mot de passe d'un autre administrateur, y compris le fondateur ;
  - désactiver des modules.

  Le vol d'un jeton devient ainsi une prise de contrôle persistante de l'entreprise. Le garde « dernier administrateur » (`UtilisateurService.java:197-202`) n'est par ailleurs pas atomique : deux administrateurs qui se retirent mutuellement le profil en même temps peuvent laisser l'entreprise sans administrateur (TOCTOU, faible).
- **Correctif** :
  - exiger `motDePasseActuel` de l'administrateur (ou un jeton « step-up » de 5 min) pour : attribuer ADMIN, changer l'adresse d'un compte, réinitialiser un mot de passe ;
  - notifier l'intéressé ;
  - pour le dernier administrateur : `SELECT … FOR UPDATE` sur les administrateurs de l'entreprise, ou une contrainte vérifiée en fin de transaction.

---

## INFO

| # | Constat | Emplacement | Statut / note |
|---|---|---|---|
| I1 | Tri accepté sur `motDePasse` et `pinHash` | `UtilisateurController.java:161-167` (`Pageable` libre) | Confirmé : `GET /api/utilisateurs?sort=pinHash,asc` → 200, ordre modifié. Impact négligeable (bcrypt salé, ADMIN seulement), mais à fermer : liste blanche des tris (`nom`, `email`, `creeLe`, `actif`). |
| I2 | Un `null` dans `roles` (`["ADMIN", null]`) provoque une `NullPointerException` (500) | `UtilisateurService.java:230-235` (`EnumSet.copyOf`) | Par le code. Ajouter `Set<@NotNull Role>` dans `UtilisateurDto.Creation/Modification`. |
| I3 | `Role.valueOf` / `PermissionAgence.valueOf` sur une saisie libre → 500 au lieu de 400 ; corps sans `@Valid` | `agence/ReglageAgenceService.java:114-120`, `AgenceController.java:100` | Par le code (ADMIN seulement). Typer `ProfilDroits` en `Role` / `Set<PermissionAgence>` pour que Jackson renvoie 400. |
| I4 | L'identité de l'entreprise (NIF, RCCM, banque, **numéro de compte bancaire**) est lisible par tous les profils (chauffeur, caissier, associé…) | `SocieteController.java:33-37` | Confirmé : caissier d'agence → 200. Acceptable, puisque ces données figurent sur les factures ; sinon renvoyer une vue réduite aux non-administrateurs. |
| I5 | `GET /api/utilisateurs/profils` ouvert à tous : révèle les modules actifs | `UtilisateurController.java:152-156` | Confirmé : caissier → 200. Sans gravité. |
| I6 | `PresenceService` charge **tous** les comptes de toutes les entreprises (`utilisateurs.findAll()`) puis filtre en Java | `presence/PresenceService.java:226`, `:239` | Pas de fuite (le filtre sur `entrepriseId` est bien présent), mais fragile et coûteux : utiliser `findByEntrepriseIdOrderByNomAscPrenomAsc`. De même `:250` `findById` sans filtre (lecture d'un e-mail par identifiant relié, saisi après contrôle `:169-171`). |
| I7 | Politique de mot de passe minimale (8 caractères, pas de contrôle de mots de passe compromis) ; `LIKE` non échappé dans la recherche (`%`, `_` agissent comme jokers, sans injection) | `UtilisateurDto.java:257`, `common/SpecificationUtils.java:24-26` | Par le code. ASVS 2.1.7 : liste de mots de passe interdits. |

---

## Points vérifiés conformes

- **Élévation vers SUPER_ADMIN** : refusée (`UtilisateurService.copie`, `:230-235`) ; le profil n'est jamais proposé (`profils()`, `:181`).
- **Profils hors modules souscrits** : refusés (`attribuables`, `:214-228`).
- **Comptes et paramétrage réservés à l'ADMIN** : création, modification, PIN, réinitialisation, activation, logo, société, modules, réglages PDV, droits des agences sont tous sous `@PreAuthorize(ADMINISTRATION)`. Testé : caissier d'agence → `GET /api/utilisateurs` 403, `GET /api/modules` 403.
- **Modification de son propre compte** : se limite à nom, prénom et téléphone (`MiseAJourProfil`). L'adresse et les profils ne sont pas modifiables par soi-même.
- **IDOR entre entreprises sur les comptes** : `charger()` impose `findByIdAndEntrepriseId`. Testé : administrateur de l'entreprise 15 → `GET /api/utilisateurs/15` (compte de l'entreprise 8) → **404**. La liste est filtrée par `entrepriseId` dans la `Specification`. Le super-admin (entreprise nulle) est inatteignable.
- **Mass assignment** : tous les DTO sont des `record` à champs explicites (aucun `entrepriseId`, `actif`, `chauffeurId`, `motDePasseAChanger`, `versionJetons`) ; `Utilisateur.entrepriseId` est `updatable = false`.
- **Effet immédiat des changements de droits** : le compte est relu en base à chaque requête (`JwtAuthenticationFilter.java:41-56`). Une désactivation ou un retrait de profil s'applique immédiatement ; un changement d'adresse ou de mot de passe invalide les jetons (`versionJetons`).
- **Cloisonnement du paramétrage** : `Societe` est cloisonnée par `@TenantId`. Sans contexte, le résolveur renvoie `AUCUNE` : `SocieteRepository.lire()` ne peut pas lire la ligne d'une autre entreprise.
- **Logo** : pas de SVG, pas de stockage disque ni de chemin, 512 Ko au plus, affichage par `data:` dans `<img>` (pas de XSS).
- **Lecture seule après expiration** : couvre `/api/utilisateurs/**` (sauf `/moi/**`), `/api/parametrage/**` et `/api/modules/**` (`WebMvcConfig.java:36-38`).
- **Requêtes sortantes** : aucun appel sortant (pas de SSRF possible : aucun `RestTemplate`, `WebClient`, `HttpClient` ni `Socket`).
- **Front** : `roleGuard('ADMIN')` sur `/utilisateurs` et `/parametrage`, en complément des contrôles serveur ; aucun `innerHTML` ni `bypassSecurityTrust*` dans ces écrans.

## Trois priorités

1. **M1** : journaliser l'octroi et le retrait des profils, ainsi que la réinitialisation des mots de passe et des PIN (le journal d'audit est aveugle sur l'élévation de privilèges).
2. **M2** : rendre provisoire tout mot de passe fixé par l'administrateur (`motDePasseAChanger = true`), pour la non-répudiation des caisses.
3. **M3** : faire du verrou PIN un état serveur, ou au minimum durcir l'empreinte hors ligne (PBKDF2 avec sel aléatoire) et appliquer la limite d'essais hors ligne.

## État des surfaces (module paramétrage)

- **Authentification** : correcte. Les mots de passe définis par l'administrateur ne sont pas provisoires (M2). Le PIN de caisse est faible côté client (M3). Le changement de mot de passe n'a pas de limite d'essais (F3).
- **Autorisation et cloisonnement** : bons. Aucune élévation, aucun IDOR et aucun mass assignment trouvés ; énumération d'adresses inter-entreprises (F1).
- **Argent et caisses** : impact indirect, par la non-répudiation (M2) et le verrou de caisse (M3).
- **Points publics** : seul le logo est exposé (F2, métadonnées).
- **Injection** : rien trouvé (Criteria API paramétrée ; `LIKE` non échappé sans risque d'injection).
- **Journalisation** : lacune M1.
- **Infra** : hors périmètre de ce module.
