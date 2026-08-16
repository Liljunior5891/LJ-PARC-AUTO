# PARC AUTO

Logiciel de gestion, de suivi et de rentabilité d'un parc de camions de location.

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
# Variables d'environnement utiles : DB_USER, DB_PASSWORD, JWT_SECRET, ADMIN_PASSWORD
mvn spring-boot:run
```

L'API démarre sur `http://localhost:8081` (port configurable via `SERVER_PORT`).
Documentation interactive : `http://localhost:8081/swagger-ui.html`.

Au premier démarrage, un compte administrateur est créé :

- email : `admin@parcauto.tg` (`parcauto.admin.email`)
- mot de passe : valeur de `ADMIN_PASSWORD`, `Admin@2026` par défaut — **à changer immédiatement**

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
| `ADMIN_PASSWORD` | `Admin@2026` | Mot de passe du compte initial |

## Périmètre livré

### Priorité 1 — Parc

- Authentification JWT et six profils utilisateurs (chapitre 40)
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
