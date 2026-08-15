# Feuille de route

Découpage du cahier des charges v2.0 en phases livrables, dans l'ordre de priorité
défini au chapitre 44.

## Phase 1 — Parc (livrée)

| Chapitre | Fonctionnalité | État |
| --- | --- | --- |
| 39, 40 | Authentification JWT, rôles et permissions | ✅ |
| 6 | Fiche camion, statuts, kilométrage | ✅ |
| 8 | Fiche chauffeur, permis, historique de statut | ✅ |
| 9 | Affectation chauffeur / camion + historique | ✅ |
| 21 | Documents et alertes d'expiration | ✅ |
| 4.1 | Tableau de bord — indicateurs de parc | ✅ (volet parc) |

## Phase 2 — Exploitation (livrée)

| Chapitre | Fonctionnalité | État |
| --- | --- | --- |
| 10 | Clients et conditions commerciales | ✅ |
| 11, 12 | Contrats de location et 5 modèles tarifaires | ✅ |
| 13 | Missions : planification, démarrage, clôture | ✅ |
| 14 | Livraisons et preuves de livraison | ✅ |
| 5 | Vue globale du parc | ✅ (hors colonnes financières) |
| 4.1 | Indicateurs d'exploitation du tableau de bord | ✅ |

Règles d'exploitation implémentées :

- numérotation automatique et atomique des pièces (`LOC-2026-0001`, `M-…`, `LIV-…`) ;
- un camion ne porte qu'un contrat actif et qu'une mission en cours à la fois,
  garanti côté application **et** par des index uniques partiels PostgreSQL ;
- l'activation d'un contrat engage le camion, sa clôture le libère ;
- une mission démarrée place camion et chauffeur en mission ; à la clôture, le
  camion retourne « en location » s'il reste sous contrat, sinon « disponible » ;
- la clôture d'une mission met à jour le compteur kilométrique du camion ;
- une livraison déclarée livrée exige une preuve : nom du réceptionnaire, signature ou photo.

## Phase 3 — Finances

| Chapitre | Fonctionnalité |
| --- | --- |
| 15 | Recettes rattachées au camion |
| 16, 17 | Dépenses par catégorie et analyse |
| 18 | Carburant, consommation et coût/km |
| 27, 28 | Facturation et suivi des paiements |
| 7, 29, 30, 31 | Rentabilité par camion, comparaison et coût réel d'exploitation |

Ces modules exigent une règle constante : **toute recette et toute dépense est
rattachée à un camion**, condition du calcul de rentabilité du chapitre 2.

## Phase 4 — GPS

| Chapitre | Fonctionnalité |
| --- | --- |
| 22 | Couche d'intégration GPS multi-fournisseurs |
| 23 | Carte du parc en temps réel (WebSocket) |
| 24 | Historique des trajets |
| 25, 26 | Géofencing et alertes |

Le champ `gps_device_id` de la table `camion` est déjà prévu pour cette phase.

## Phase 5 — Maintenance

| Chapitre | Fonctionnalité |
| --- | --- |
| 19 | Entretiens, seuils de vidange et alertes |
| 20 | Suivi des pneus par position |
| 33 | Incidents déclarés par les chauffeurs |

## Phase 6 — Pilotage et mobile

| Chapitre | Fonctionnalité |
| --- | --- |
| 4, 41 | Tableau de bord complet et KPI de direction |
| 35 | Rapports exportables PDF / Excel / CSV |
| 36 | Fiche synthétique du camion |
| 32 | Application mobile chauffeur |
| 34 | Notifications |

## Principes techniques transverses

- Migrations Flyway versionnées, jamais de modification d'une migration livrée.
- `ddl-auto: validate` : le schéma est décrit par les migrations, pas par Hibernate.
- Suppression logique (archivage) pour toute entité porteuse d'historique.
- Montants en `NUMERIC(16,2)`, devise FCFA, pas de virgule flottante.
- Contrôles d'accès par rôle sur chaque contrôleur (`@PreAuthorize`).
