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

## Phase 3 — Finances (livrée)

| Chapitre | Fonctionnalité | État |
| --- | --- | --- |
| 15 | Recettes rattachées au camion | ✅ |
| 16, 17 | Dépenses par catégorie et nature, analyse | ✅ |
| 18 | Carburant, consommation plein à plein, coût/km | ✅ |
| 27, 28 | Facturation, règlements et créances | ✅ |
| 7 | Fiche performance du camion | ✅ (hors jours d'immobilisation) |
| 29, 30, 31 | Rentabilité globale, comparaison, coût réel et marge | ✅ |
| 4.2 | Indicateurs financiers du tableau de bord | ✅ |
| 5 | Colonnes financières de la vue globale du parc | ✅ |

Règle constante appliquée : **toute recette et toute dépense est rattachée à un
camion** (colonne `camion_id` non nulle), condition du calcul de rentabilité du
chapitre 2. Autres règles :

- `résultat = recettes − dépenses`, par camion puis agrégé au parc ;
- les dépenses refacturées au client sont exclues du résultat ;
- un plein de carburant exige un relevé de compteur, sans quoi la consommation
  serait incalculable ;
- l'émission d'une facture génère une recette par camion facturé ;
- une facture émise n'est plus modifiable, une facture réglée n'est pas annulable.

Les **jours d'immobilisation** de la fiche performance restent à zéro tant que le
module Maintenance (phase 5) n'alimente pas cette donnée.

## Phase 4 — GPS (livrée)

| Chapitre | Fonctionnalité | État |
| --- | --- | --- |
| 22 | Couche d'intégration multi-fournisseurs | ✅ |
| 23 | Carte du parc, marqueurs par statut | ✅ |
| 24 | Historique des trajets | ✅ |
| 25 | Géofencing sur zones circulaires | ✅ |
| 26 | Alertes GPS | ✅ |

Points de conception :

- **aucune dépendance à un fabricant** : chaque fournisseur a son adaptateur qui
  traduit vers un format pivot (`PositionBrute`). Ajouter un traceur = une classe,
  aucune autre modification. Deux adaptateurs fournis : format pivot et Traccar ;
- les positions arrivent par `POST /api/gps/positions/{fournisseur}` et sont
  rattachées au camion par le `gpsDeviceId` de sa fiche ;
- la fenêtre d'anti-répétition des alertes se calcule sur l'horodatage de la
  position, jamais sur l'heure courante : une passerelle qui rattrape un retard
  réseau ne doit pas produire une alerte par trame ;
- les positions et alertes sont diffusées par WebSocket STOMP (`/topic/gps/*`) ;
  la carte utilise pour l'instant un rafraîchissement périodique de 20 s.

Les zones sont **circulaires** (centre + rayon). Des zones polygonales
nécessiteraient PostGIS et ne sont pas requises par le cahier des charges.

## Phase 5 — Maintenance (livrée)

| Chapitre | Fonctionnalité | État |
| --- | --- | --- |
| 19 | Entretiens, échéances et alertes | ✅ |
| 20 | Suivi des pneus par position | ✅ |
| 33 | Incidents déclarés depuis le terrain | ✅ |

Règles de maintenance :

- l'échéance d'une intervention est **kilométrique, calendaire ou les deux** :
  la première atteinte déclenche l'alerte (marge de 1 000 km ou 15 jours) ;
- réaliser une intervention **génère la dépense** rattachée au camion : sans ce
  lien, le coût de maintenance échapperait au calcul de rentabilité du chapitre 2 ;
- la réalisation met à jour le compteur, libère le camion et **planifie
  l'occurrence suivante** d'après la périodicité ;
- une position de roue ne porte qu'un pneu monté à la fois, garanti par un index
  unique partiel ; le kilométrage propre de chaque pneu donne son coût au km ;
- un incident immobilisant place le camion en `IMMOBILISE` ou `ACCIDENTE` et ne le
  libère qu'une fois **tous** les incidents ouverts résolus.

## Phase 6 — Pilotage (livrée, hors mobile)

| Chapitre | Fonctionnalité | État |
| --- | --- | --- |
| 4, 41 | Tableau de bord et KPI de direction | ✅ |
| 34 | Notifications consolidées | ✅ |
| 35 | Rapports exportables | ✅ PDF (9 rapports) + CSV |
| 36 | Fiche synthétique du camion | ✅ |
| 32 | Application mobile chauffeur | ❌ non démarrée |

Le service de pilotage n'a **aucune donnée propre** : il assemble celles des cinq
modules précédents. Les notifications interrogent chaque module et présentent une
liste unique et ordonnée, chaque entrée pointant vers l'écran concerné.

Restent à faire :

- **export Excel natif** (chapitre 35). Le PDF est livré — menu **Rapports**, neuf
  rapports dont le global du parc et la fiche complète d'un ou plusieurs camions
  (`RapportPdfService`, mise en page commune dans `MiseEnPagePdf`). Le CSV reste
  proposé et s'ouvre dans Excel — séparateur point-virgule, virgule décimale et
  BOM UTF-8 pour les accents ; un `.xlsx` natif demanderait Apache POI ;
- **application mobile chauffeur** (chapitre 32). C'est un projet Flutter ou React
  Native distinct. L'API est prête : les endpoints de mission (démarrage, clôture),
  de dépense, de livraison et d'incident acceptent déjà le rôle `CHAUFFEUR`.

## Principes techniques transverses

- Migrations Flyway versionnées, jamais de modification d'une migration livrée.
- `ddl-auto: validate` : le schéma est décrit par les migrations, pas par Hibernate.
- Suppression logique (archivage) pour toute entité porteuse d'historique.
- Montants en `NUMERIC(16,2)`, devise FCFA, pas de virgule flottante.
- Contrôles d'accès par rôle sur chaque contrôleur (`@PreAuthorize`).


## Ajustements demandés après recette (16/08/2026)

Corrections issues du fichier `les remarques.txt` :

1. **Le camion est identifié par sa plaque.** Le numéro interne faisait doublon :
   colonne supprimée (migration V6), recherche, tris et écrans réalignés sur
   l'immatriculation.
2. **Référentiel de marques avec logo.** Table `marque_camion` avec 21 marques
   courantes en Afrique de l'Ouest. Le champ `logoUrl` accepte un fichier fourni
   par l'entreprise ; à défaut, l'interface affiche un monogramme aux couleurs de
   la marque. Les logos sont des marques déposées : ils ne sont pas embarqués.
3. **Volets RECETTES et DÉPENSES sur la fiche du camion.** RECETTES liste les
   contrats du véhicule et permet d'en créer un nouveau, camion pré-sélectionné ;
   DÉPENSES liste ses dépenses et ouvre le formulaire de saisie pré-rempli.
4. **Menus Locations, Missions et Livraisons retirés.** Les routes restent
   actives : ces écrans sont désormais atteints depuis la fiche du camion.
5. **Facture PDF.** Après enregistrement d'une recette rattachée à un client,
   l'application propose de générer la facture puis de télécharger son PDF
   (OpenPDF, mentions émetteur / client / période / TVA / échéance).

Les coordonnées de l'entreprise portées sur la facture se règlent par variables
d'environnement : `ENTREPRISE_NOM`, `ENTREPRISE_ADRESSE`, `ENTREPRISE_TELEPHONE`,
`ENTREPRISE_NIF`.


## Deuxième série d'ajustements (17/08/2026)

1. **Type et couleur en référentiels.** Tables `type_camion` (13 entrées) et
   `couleur_camion` (12 entrées avec code hexadécimal), migration V7. Le libellé
   libre reste accepté pour une valeur hors liste.
2. **Listes déroulantes avec recherche.** Composant `app-select-recherche`, écrit
   sans dépendance : filtre insensible aux accents, pastilles de couleur, logos de
   marque. Il implémente `ControlValueAccessor`, donc s'utilise comme un champ
   ordinaire (`formControlName` ou `ngModel`). Déployé sur les listes de données
   (camion, chauffeur, marque, type, couleur, type de document) ; les listes de
   statuts, courtes et fermées, restent des `select` natifs.
3. **Création d'un chauffeur depuis l'affectation.** Formulaire réduit aux champs
   indispensables ; le chauffeur créé est immédiatement sélectionné.
4. **Garant du chauffeur.** Nom, lien, deux téléphones, adresse, profession et
   pièce d'identité, plus le téléversement des dossiers du chauffeur et du garant
   (trois types de documents dédiés).
5. **Volet ODOMÈTRE** sur la fiche du camion, à côté de RECETTES et DÉPENSES.
   Aucune table dédiée : les relevés proviennent des missions, des pleins, des
   interventions et des trames GPS, avec la distance entre deux relevés.
6. **Graphiques du tableau de bord.** Recettes / dépenses sur six mois (une seule
   échelle, jamais deux axes) et répartition des dépenses par catégorie. La palette
   catégorielle est validée par `scripts/validate_palette.js` du référentiel de
   visualisation : bande de clarté, chroma, séparation daltonisme et contraste.
   Chaque barre est directement étiquetée, l'ordre des couleurs est fixe.
7. **Vue du parc en blocs.** Un bloc par camion — immatriculation, marque, modèle,
   dernier kilométrage — dont le bandeau porte la couleur du statut. Les recettes,
   dépenses et résultat du mois se dévoilent au survol ; sur écran tactile, où le
   survol n'existe pas, ils restent visibles.
