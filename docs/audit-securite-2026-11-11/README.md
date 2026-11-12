# Audit sécurité du 2026-11-11 (agent expert-security)

Audit en lecture seule, module par module, sur la branche `saas`. Aucun fichier modifié pendant l'audit.
Chaque rapport donne pour chaque constat : fichier:ligne, preuve, scénario d'exploitation, correctif.
`[x]` corrigé, `[~]` corrigé en partie (le reste est indiqué).

| # | Module | Rapport | Crit. | Élevé | Moyen | Faible | Info |
|---|---|---|---|---|---|---|---|
| 1 | Socle (security, config, common) | [securite-01-socle.md](securite-01-socle.md) | 0 | 2 | 6 | 9 | 4 |
| 2 | Plateforme (super-admin, abonnements) | [securite-02-plateforme.md](securite-02-plateforme.md) | 0 | 0 | 2 | 5 | 5 |
| 3 | Paramétrage | [securite-03-parametrage.md](securite-03-parametrage.md) | 0 | 0 | 3 | 4 | 7 |
| 4 | Parc | [securite-04-parc.md](securite-04-parc.md) | 0 | 0 | 5 | 5 | 4 |
| 5 | Exploitation | [securite-05-exploitation.md](securite-05-exploitation.md) | 0 | 1 | 3 | 5 | – |
| 6 | Finance | [securite-06-finance.md](securite-06-finance.md) | 0 | 0 | 3 | 6 | 4 |
| 7 | Maintenance | [securite-07-maintenance.md](securite-07-maintenance.md) | 0 | 0 | 3 | 6 | 3 |
| 8 | GPS | [securite-08-gps.md](securite-08-gps.md) | 0 | 2 | 3 | 4 | – |
| 9 | Pilotage | [securite-09-pilotage.md](securite-09-pilotage.md) | 0 | 0 | 1 | 3 | 5 |
| 10 | Point de vente | [securite-10-pointdevente.md](securite-10-pointdevente.md) | 0 | 1 | 2 | 8 | 2 |
| 11 | Agences multiservices | [securite-11-agence.md](securite-11-agence.md) | 0 | 2 | 5 | 7 | 4 |
| 12 | Marketplace | [securite-12-marketplace.md](securite-12-marketplace.md) | 0 | 1 | 5 | 6 | 4 |
| 13 | Présence | [securite-13-presence.md](securite-13-presence.md) | 0 | 2 | 3 | 6 | – |
| | **Total** | | **0** | **11** | **44** | **74** | |

## Corrections appliquées le 2026-11-11 (non commitées, migration V78)

Compilation backend et frontend OK, 186 tests backend au vert, migration V78 essayée sur `parcauto_saas` dans une transaction annulée. Rien n'a été rejoué sur l'API en marche (8085 tourne encore sur l'ancien jar).

### Constats élevés

- [x] Socle E-1 : démarrage refusé par défaut avec la clé JWT de dev, une clé < 64 octets, le mot de passe ou l'adresse super-admin par défaut, ou le paiement simulé ; `PARCAUTO_DEV=true` pour un poste de dev. JWT : HS512 imposé, émetteur et audience exigés.
- [x] Socle E-2 : 20 échecs / 15 min par IP et 5 par compte, avant toute vérification ; file du journal bornée à 10 000 lignes ; mot de passe limité à 72 octets.
- [x] Exploitation E1 : livraisons limitées au chauffeur concerné (liste et détail), contrôleur en `TERRAIN`.
- [x] GPS E1 : nombres des adaptateurs validés (pas de notation exponentielle en texte), taille du lot contrôlée avant conversion, valeurs bornées, position fautive écartée seule.
- [~] GPS E2 : trames de plus de 24 h refusées, compteur plafonné, jeton conseillé en en-tête, génération et révocation journalisées. **Reste** : un jeton par traceur (refonte).
- [x] PDV E-1 : remise du caissier plafonnée (réglage, 10 % par défaut), référence obligatoire pour mobile money et carte, règlement fournisseur en espèces refusé sous validation (hors admin).
- [x] Agences E-1 : résumé du conflit construit par le serveur ; à la reprise, commission du barème et compte par défaut du superviseur.
- [x] Agences E-2 : module et droits exigés, 200 opérations / 8 Ko max, agence contrôlée avant toute trace, limiteur par utilisateur.
- [~] Présence E1 : QR valable 60–90 s, marge GPS 30 m, distance non renvoyée. **Reste** : lier le téléphone à l'employé.
- [~] Présence E2 : blocage progressif sans remise à zéro, compteur global de 50 PIN faux/h par entreprise, compteurs par lieu et adresse. **Reste** : identifiant en plus du PIN.
- [x] Marketplace E1 : 100 unités max par commande, 3 commandes en attente max par téléphone, expiration automatique (30 min sans paiement mobile money, 48 h sans confirmation) qui rend le stock.

### Thèmes transverses

- [x] Désérialiseur JSON global des `BigDecimal` (15 chiffres entiers, 6 décimales) + bornes `@Max` sur les entiers concernés.
- [x] `@PreAuthorize` ajoutés : véhicules, incidents, livraisons, missions, GPS (carte, alertes, zones), catalogue PDV (prix d'achat masqué hors gérant), marketplace par famille d'actions, hors ligne agences, suivi des cartes ; règle d'URL `/api/plateforme/**`.
- [x] Canaux STOMP réservés à ADMIN/GESTIONNAIRE_PARC, compte relu en base à chaque abonnement.
- [x] Compteur kilométrique : une seule règle (`CompteurKilometrique`, saut max 5 000 km) pour missions, entretiens, pleins validés et GPS.
- [x] Suspension et lecture seule appliquées aux points publics à jeton (`AccesPublicInterceptor`).
- [x] Justificatifs figés après décision (parc, agences), ancien fichier conservé, remplacements tracés ; logo et photos décodés après contrôle des dimensions et ré-encodés.
- [x] Validation des dépenses : dépense en attente figée pour le gestionnaire, annulations réservées à l'admin, correction re-contrôlée ; agences : tout compte passe par le circuit ; PDV : règlement fournisseur en espèces.
- [x] Tri sur champ interne refusé (`TriAutorise`), tri inconnu, identifiant mal formé et conflit de version en 400/409.
- [x] Verrou PIN : empreinte PBKDF2 à sel aléatoire, 5 erreurs hors ligne effacent l'empreinte, purge à la déconnexion ; verrou exigé par le serveur (réponse 423 `ECRAN_VERROUILLE` jusqu'au code PIN, par session, à la connexion, au verrouillage de l'écran et après le délai d'inactivité + 2 min). État en mémoire : un seul serveur d'API, ou état partagé à prévoir si plusieurs.
- [x] Mots de passe fixés par l'admin ou le super-admin : provisoires.
- [~] Paiement : simulation refusée hors `PARCAUTO_DEV`, montant comparé au total, référence aléatoire. **Reste** au branchement d'un agrégateur : signature du webhook, devise, idempotence.
- [~] Production : Swagger fermé par défaut, en-têtes CSP/HSTS/Referrer sur l'API, Postgres lié à 127.0.0.1, logs en INFO, `*.log` ignorés, `X-Forwarded-For` lu seulement via les proxys de confiance. **Reste** : Spring Boot 3.4.1 à monter, rôle base non superutilisateur, CSP du frontend, mise à jour Angular.

### Autres corrections notables

Plateforme : un paiement en attente par entreprise et références uniques (index), module non tarifé refusé, limites de formule sous verrou. Paramétrage : profils, PIN, réinitialisations et activations journalisés ; dernier administrateur sous verrou ; messages d'adresse neutres ; limite d'essais sur l'ancien mot de passe. Parc : pièces PDV fermées à `/api/documents`, double règlement fournisseur verrouillé. Exploitation : contrat facturé figé, fin future refusée, recettes masquées au chauffeur, observations ajoutées sans écraser. Finance : rentabilité bornée à 5 ans, une facture active par mission/contrat (index), motif d'annulation obligatoire, demande de location conclue seulement sur le bon véhicule et client, recettes supprimables par l'admin seul sous validation. Maintenance : bornes des périodicités et kilométrages, pneu figé après dépense ou rebut. Pilotage : export PDF limité à 50 véhicules et 10 exports/min. PDV : sessions de caisse verrouillées à la fermeture, stock jamais négatif (CHECK), CSV neutralisés, commandes de salle et impressions limitées aux caisses de l'utilisateur, jetons QR des tables masqués hors gérant, pas de validation de sa propre remise. Agences : superviseur ne ferme pas sa caisse, mouvements refusés sur caisse fermée, commission ≤ montant, soldes sans débordement, lot de cartes borné. Marketplace : images et descriptions bornées, rôles par famille, paiement manuel journalisé, suivi public sans identifiants internes ni immatriculation, stock affiché plafonné à 20, prix payé en ligne reporté en salle. Présence : création d'employé et PIN réservés à ADMIN/GERANT, jeton public renouvelé avec le secret, codes d'appairage expirés purgés, compteur global d'appairage.

### Restes (décision ou chantier à part)

- Inscription publique : vérification de l'adresse par lien et captcha (il faut un service d'e-mail).
- Jeton court + jeton de rafraîchissement en cookie ; second facteur pour le super-admin ; ressaisie du mot de passe pour les opérations sensibles.
- GPS : jeton par traceur. Présence : téléphone lié à l'employé, identifiant + PIN, jeton de borne rotatif ou en cookie, traçabilité de l'appareil.
- PDV : mode « Autre » réservé au gérant, validation des dépenses de caisse activée par défaut (décision métier), rotation des QR de table.
- Agences : rapprochement des commissions plafonné, détail des dépenses limité au caissier auteur, plafond cumulé, verrous résiduels (F5), rôle base dédié (F6).
- Finance : origine des recettes (F3), approbation des annulations de facture. Parc : liaison compte chauffeur ↔ fiche, contrôle des signatures de fichiers.
- Marketplace : vignettes servies par URL et ré-encodage serveur des images, prédicat commun « offre visible ».

## Notes sur les tests

- Les agents ont signé localement des jetons de test avec la clé JWT de dev pour éviter d'écrire des lignes de connexion dans le journal. Script : scratchpad `tests/jwt.js` (hors dépôt).
- Plusieurs constats ne sont confirmés que par le code (pas de compte chauffeur de test, écritures refusées). À rejouer avec l'accord du propriétaire : exploitation E1/M1/M3, agences E-1/E-2 (comptes `caisse.agence@test.local`, `sup.agence@test.local`), finance M1 sur l'API.
