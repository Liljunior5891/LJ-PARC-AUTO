# LJ SaaS

Plateforme SaaS de gestion pour entreprises. Chaque entreprise crée son compte
en libre-service, active les modules dont elle a besoin et travaille sur ses
propres données, isolées de celles des autres entreprises.

| Module | État |
| --- | --- |
| **Gestion de parc** — véhicules, chauffeurs, locations, missions, finances, GPS, maintenance, rentabilité | Disponible |
| **Point de vente** — deux types d'activité choisis à l'activation : **Boutique** (caisse rapide : douchette, panier, ticket 80 mm) ou **Bar / restaurant / café** (plan de salle, commandes par table sur tablette, écrans cuisine et bar, addition partagée par convive). Socle commun : articles et catégories, stock et inventaires, sessions de caisse avec écart, encaissement espèces / mobile money / carte, annulations, tableau de bord | Disponible (v1) |
| **Marketplace** — boutique en ligne publique par entreprise (`/boutique/{adresse}`, lien et QR) et place de marché commune (`/marche`). Offres : articles du point de vente (prix, photo, stock en direct), véhicules du parc à louer, produits libres. Commande sans compte (nom + téléphone), retrait ou livraison, paiement à la remise ou mobile money, suivi par lien (`/suivi/{jeton}`) ; demande de location acceptée = contrat. Paiement en ligne : fournisseur à brancher (`parcauto.paiement.fournisseur`, `simulation` en développement) | Disponible (v1) |

## Architecture multi-entreprises

- **Isolation des données** : chaque table métier porte `entreprise_id`. Les entités
  héritent de `BaseEntity`, dont le champ `@TenantId` fait filtrer par Hibernate
  toutes les lectures (y compris par identifiant) et poser l'entreprise à
  l'insertion. L'entreprise courante vient du compte connecté
  (`plateforme.tenant.ContexteEntreprise`) ; sans contexte, aucune donnée métier
  n'est visible. Les deux requêtes SQL natives (numérotation, évolution mensuelle)
  filtrent explicitement. Les canaux WebSocket sont propres à chaque entreprise.
- **Modules** : catalogue dans `plateforme.module.ModuleApplicatif`, souscriptions
  dans `entreprise_module`. Un contrôleur annoté `@ModuleRequis(...)` répond 403
  tant que l'entreprise n'a pas activé le module ; côté Angular, `moduleGuard`
  et le menu suivent les modules de la session.
- **Ajouter un module** : une constante dans `ModuleApplicatif` (`disponible = true`),
  ses entités héritant de `BaseEntity`, ses contrôleurs annotés `@ModuleRequis`,
  ses routes Angular sous un parent `moduleGuard('CODE')`, ses onglets de menu
  marqués `module: 'CODE'` dans `shell.ts`, et son entrée dans `PRESENTATION_MODULES`.
- **Variantes de module** : un module peut se décliner (`ModuleApplicatif.getVariantes()`),
  choisies à l'activation et modifiables par l'administrateur (`entreprise_module.variante`).
  `@ModuleRequis(value = ..., variante = "RESTAURATION")` réserve un contrôleur à une
  variante ; un `GardeVariante` refuse un changement qui laisserait des données en plan
  (tables encore ouvertes). La session expose `variantes`, que suivent le menu et `varianteGuard`.
- **Profils** : `SUPER_ADMIN` (exploitant de la plateforme : console des entreprises,
  suspension, sans accès aux données métier), puis par entreprise `ADMIN` (tous
  les modules), `GESTIONNAIRE_PARC` et `CHAUFFEUR` (Gestion de parc), `GERANT`
  et `CAISSIER` (Point de vente), `SERVEUR` et `PREPARATEUR` (cuisinier / barman, Point
  de vente en mode restaurant). Chaque module déclare ses profils
  (`ModuleApplicatif.getProfils()`) : un caissier n'accède pas au parc même si
  l'entreprise a activé les deux modules.
- **Reprise de l'existant** : la migration V13 rattache les données d'une
  installation mono-entreprise à une première entreprise (nom repris du
  paramétrage), avec le module Gestion de parc activé ; ses comptes sont conservés.

Le module Gestion de parc implémente le cahier des charges v2.0 (`cahier de charge.txt`).

### Point de vente en mode bar / restaurant / café

- **Plan de salle** : salles et tables dessinées par le gérant (glisser-déposer, formes,
  places), puis plan de service coloré selon l'état de chaque table (libre, occupée,
  articles à envoyer, prêt à servir, addition demandée), rafraîchi en continu.
- **Commande par table** (tablette du serveur) : articles saisis en attente avec consignes
  (« sans piment »…), puis envoyés d'un geste. Chaque envoi forme un bon par poste ; le poste
  vient de la catégorie (cuisine, bar ou servi directement). Le stock sort à l'envoi.
- **Écrans cuisine et bar** : bons à préparer, en préparation, prêts ; signal sonore,
  retard signalé à 10 et 20 minutes, plein écran.
- **Addition** imprimée pour la table, puis **encaissement** en une fois ou **par convive**
  (une quantité partielle scinde la ligne). La commande se ferme quand tout est réglé ;
  annuler le ticket d'une addition rouvre la commande.
- Commandes au comptoir ou à emporter, transfert de table, couverts. Une ligne envoyée ne
  s'annule que par un gérant (retour en stock).
- **Commande par code QR** : chaque table a un jeton imprévisible imprimé dans son code QR
  (écran **Codes QR**, impression en chevalets, renouvellement). Le client ouvre la carte
  en ligne (`/t/{jeton}`, sans compte), compose son panier avec des précisions, suit sa
  commande, appelle un serveur ou demande l'addition ; il règle à la fin auprès du serveur.
  Selon le réglage, un serveur valide la demande sur le plan de salle avant la cuisine
  (par défaut) ou elle part directement en préparation. Garde-fous : quantités bornées,
  envois en rafale freinés par table, carte publique sans coûts ni stocks chiffrés.
  L'API publique (`/api/public/qr/{jeton}`) trouve l'entreprise par le jeton
  (`ContexteTableInterceptor`) puis exige le module en mode restaurant
  (`@ModuleRequis(..., publique = true)`).

Implémentation du cahier des charges v2.0 (`cahier de charge.txt`).

## Architecture

| Composant | Technologie | Dossier |
| --- | --- | --- |
| API REST | Spring Boot 3.4 / Java 21 | `backend/` |
| Interface web | Angular 20 (standalone, signals) | `frontend/` |
| Base de données | PostgreSQL 16 + migrations Flyway | `backend/src/main/resources/db/migration` |
| Application mobile chauffeur | Flutter ou React Native (phase ultérieure) | — |

## Démarrage rapide

### 1. Base de données

Avec le PostgreSQL local :

```bash
createdb -U postgres parcauto
```

Ou avec Docker (conteneur exposé sur le port 5433 pour ne pas entrer en conflit
avec un PostgreSQL déjà installé) :

```bash
docker compose up -d
export DB_PORT=5433 DB_USER=parcauto DB_PASSWORD=parcauto
```

### 2. API

```bash
cd backend
# Variables d'environnement utiles : DB_USER, DB_PASSWORD, JWT_SECRET, PLATEFORME_ADMIN_PASSWORD
mvn spring-boot:run
```

L'API démarre sur `http://localhost:8081` (port configurable via `SERVER_PORT`).
Documentation interactive : `http://localhost:8081/swagger-ui.html`.

Au premier démarrage, un compte administrateur est créé :

- c'est le compte **administrateur de la plateforme** (`SUPER_ADMIN`), qui ouvre la console `/plateforme`
- email : valeur de `PLATEFORME_ADMIN_EMAIL`, `superadmin@plateforme.local` par défaut
- mot de passe : valeur de `PLATEFORME_ADMIN_PASSWORD`, `Plateforme@2026` par défaut — **à changer immédiatement**

Les entreprises créent ensuite leur compte depuis `/inscription` ; leur premier
utilisateur en devient l'administrateur.

### 3. Interface web

```bash
cd frontend
npm start
```

L'application est servie sur `http://localhost:4200`.

## Configuration

| Variable | Défaut | Rôle |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `parcauto` | Connexion PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | `postgres` / `postgres` | Identifiants PostgreSQL |
| `JWT_SECRET` | clé de développement | Signature des jetons — **obligatoire en production** |
| `JWT_EXPIRATION_MINUTES` | `480` | Durée de validité d'une session |
| `CORS_ORIGINS` | `http://localhost:4200` | Origines autorisées |
| `DOCUMENTS_DIR` | `./data/documents` | Stockage des justificatifs |
| `PLATEFORME_ADMIN_EMAIL` | `superadmin@plateforme.local` | Compte de l'exploitant de la plateforme |
| `PLATEFORME_ADMIN_PASSWORD` | `Plateforme@2026` | Mot de passe **provisoire** de ce compte : il doit être changé à la première connexion |
| `PARCAUTO_PRODUCTION` | `false` | `true` (ou profil Spring `prod`) : l'API refuse de démarrer avec la clé `JWT_SECRET` ou le mot de passe administrateur par défaut |

## Périmètre livré

### Priorité 1 — Parc

- Écran **Paramétrage** (chapitre 39, administrateur) : nom, sigle, forme juridique,
  coordonnées, NIF, RCCM, banque, mentions de bas de facture et logo. Ces
  informations alimentent l'en-tête des factures et des rapports PDF, le nom de
  la barre latérale et le logo de la page de connexion
- Authentification JWT et gestion des comptes (chapitre 40) : écran **Utilisateurs**
  réservé à l'administrateur — création, profils, activation, réinitialisation du
  mot de passe — et écran **Mon compte** où chacun change le sien.
  Trois profils : `ADMIN` (accès complet et gestion des comptes),
  `GESTIONNAIRE_PARC` (toute l'activité) et `CHAUFFEUR` (actions de terrain :
  démarrage et clôture de mission, confirmation de livraison, déclaration
  d'incident, relevé du compteur)
- Camions : fiche complète, statuts, kilométrage, archivage (chapitre 6)
- Chauffeurs : fiche, permis, alertes d'expiration (chapitre 8)
- Affectations chauffeur / camion avec historique et contrôle de chevauchement (chapitre 9)
- Documents : justificatifs téléversés et alertes d'échéance (chapitre 21)
- Tableau de bord parc : effectifs, statuts, alertes (chapitre 4, volet parc)

### Priorité 2 — Exploitation

- Clients : fiche, conditions commerciales, historique des contrats (chapitre 10)
- Contrats de location : cycle de vie complet et 5 modèles tarifaires (chapitres 11 et 12)
- Missions : planification, démarrage, clôture avec relevés de compteur (chapitre 13)
- Livraisons : bon, destinataire, marchandise et preuve de livraison (chapitre 14)
- Vue globale du parc : camion, chauffeur, client, activité en cours (chapitre 5)
- Tableau de bord : indicateurs d'exploitation et taux d'utilisation (chapitre 4.1)

### Priorité 3 — Finances

- Recettes rattachées au camion, avec répartition par type (chapitre 15)
- Dépenses par catégorie et nature détaillée, avec analyse (chapitres 16 et 17)
- Carburant : consommation plein à plein, coût/km, prix moyen du litre (chapitre 18)
- Facturation : génération depuis un contrat ou une mission, TVA, règlements, créances (chapitres 27 et 28)
- Rentabilité : fiche performance du camion, comparaison du parc, coût réel et marge (chapitres 7, 29, 30, 31)
- Tableau de bord : chiffre d'affaires, dépenses, résultat et créances (chapitre 4.2)

### Priorité 4 — GPS

- Couche d'intégration multi-fournisseurs, sans dépendance à un fabricant (chapitre 22)
- Carte du parc : marqueurs colorés par statut, popup camion/chauffeur/mission (chapitre 23)
- Historique des trajets : itinéraire, distance, vitesses, arrêts (chapitre 24)
- Géofencing : siège, dépôts, agences, zones interdites (chapitre 25)
- Alertes : excès de vitesse, zone interdite, mouvement hors mission, hors horaires (chapitre 26)

### Priorité 5 — Maintenance

- Entretiens : échéances kilométriques et calendaires, alertes, réalisation générant la dépense (chapitre 19)
- Pneumatiques : suivi individuel par position, usure et coût au kilomètre (chapitre 20)
- Incidents : déclaration terrain, immobilisation automatique, ouverture d'une réparation (chapitre 33)

### Priorité 6 — Pilotage

- KPI de direction : parc, finances, exploitation (chapitre 41)
- Fiche synthétique du camion, toutes informations réunies (chapitre 36)
- Notifications consolidées de tous les modules (chapitre 34)
- Menu **Rapports** : neuf rapports consultables à l'écran puis téléchargeables
  en PDF (parc global, fiche
  complète d'un ou plusieurs camions, financier, recettes, dépenses, carburant,
  maintenance, chauffeurs, créances) ; export CSV conservé pour les tableurs
  (chapitre 35 — le `.xlsx` natif reste à ajouter)

L'application mobile chauffeur (chapitre 32) n'est pas démarrée ; l'API accepte déjà
le rôle `CHAUFFEUR` sur les missions, dépenses, livraisons et incidents.

La suite du programme est décrite dans [docs/FEUILLE-DE-ROUTE.md](docs/FEUILLE-DE-ROUTE.md).

## Tests

```bash
cd backend && mvn test          # regles metier
cd frontend && npm test         # composants Angular
./scripts/verifier-api.sh       # test de fumee sur une API demarree
```

Note : les requetes de recherche utilisent des Specifications JPA plutot que des
clauses `:param is null` en JPQL — PostgreSQL ne sait pas typer un parametre nul
isole et rejette la requete.

## Structure du dépôt

```
backend/    API Spring Boot (domaine parc, sécurité, migrations)
frontend/   Application Angular (tableau de bord, parc, chauffeurs, documents)
docs/       Feuille de route et notes d'architecture
```
