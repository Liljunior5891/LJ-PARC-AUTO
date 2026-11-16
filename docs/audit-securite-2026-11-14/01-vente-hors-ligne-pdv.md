# Audit sécurité 2026-11-14, sujet 1 : vente hors ligne du point de vente (V88, V90)

Date : 2026-11-14. Branche `saas`, changements non commités.

## Périmètre

**Backend**
- `pointdevente/horsligne/` : `HorsLignePdvController`, `HorsLignePdvService`, `VenteHorsLignePdv`, `VenteHorsLignePdvRepository`.
- `pointdevente/vente/VenteService` : `encaisser`, `encaisserHorsLigne`, `ticketParCle`.
- `pointdevente/vente/VenteController` : rattrapage de l'envoi double.
- `VenteDto` : `VenteHorsLigne`, `LigneHorsLigne`, `cleHorsLigne`.
- Migrations `V88__ventes_hors_ligne_pdv.sql` et `V90__cle_vente_hors_ligne.sql`.

**Frontend**
- `core/services/pdv-hors-ligne.service.ts`.
- `pages/pdv/caisse/caisse.ts`.
- `caisseBoutiqueGuard` dans `core/auth/auth.guard.ts`.
- Page `pages/pdv/hors-ligne/`.

## Méthode

1. Revue statique.
2. Tests dynamiques sur une API de test : jar `parcauto-api-v98-revue.jar`, port 8095, base `parcauto_e2e`.

**Comptes utilisés**
- `quincaillerie@test.local` : entreprise 8, session de caisse 13.
- `agences@test.local` : entreprise 15. Le module Point de vente a été activé le temps du test, puis désactivé.

**Traces laissées dans `parcauto_e2e`**
- Traces hors ligne 12 à 23.
- Ventes 129, 130, 131, 135, 139 et 143 : toutes annulées ensuite.
- Conflits : abandonnés avec la note « Essai d'audit de securite ».
- Activation puis désactivation du module PDV de l'entreprise 15.
- Entrées de journal correspondantes.

L'API 8085 et la base `parcauto_saas` n'ont pas été touchées.

## Ce qui tient (confirmé)

| Contrôle | Résultat | Preuve |
|---|---|---|
| Cloisonnement : liste, nombre, reprise, abandon d'une vente d'une autre entreprise | Bloqué | Entreprise 15 : `GET /api/pdv/hors-ligne` → `[]`, `conflits/nombre` → `0`. `POST /api/pdv/hors-ligne/13/abandon` et `/13/reprise` (trace de l'entreprise 8) → `404 "Vente hors ligne introuvable (id=13)"`, même message qu'un identifiant inexistant, donc aucun oracle. |
| Rejeu d'une clé d'une autre entreprise | Ne rend rien | L'entreprise 15 renvoie la clé `audit-098e…` de l'entreprise 8 : nouvelle trace propre à l'entreprise 15, statut `CONFLIT`, `"Article introuvable (id=31)"`. Le résultat de l'entreprise 8 ne fuit pas. `POST /api/pdv/ventes` avec `cleHorsLigne` de l'entreprise 8 → `404 Article introuvable` : pas son ticket. Contrainte `UNIQUE (entreprise_id, cle)` en V88 et index partiel `(entreprise_id, cle_hors_ligne)` en V90. |
| Article d'une autre entreprise dans une vente hors ligne | Refusé | Entreprise 8 avec l'article 21 de l'entreprise 2 → `CONFLIT "Article introuvable (id=21)"`. `findById` est bien filtré par `@TenantId`. |
| Prix inférieur au catalogue | Passe en conflit | Prix 10 au lieu de 1000 → `CONFLIT "Prix de Fer de 16 plus bas que le catalogue (10 F au lieu de 1000 F) : a valider par le gerant"`. La reprise par l'administrateur donne `REPRISE`, `ecartsPrix` renseigné. |
| Rejeu de la même clé | Aucune seconde vente | Le second envoi, même avec un prix de 1, rend le même `venteId` 129. |
| Droits | Conformes | Synchronisation `PDV_CAISSE` (ADMIN, GERANT, CAISSIER). Liste, reprise et abandon `PDV_GESTION` (ADMIN, GERANT). `@ModuleRequis(POINT_DE_VENTE)`. Verrou d'écran (423) et lecture seule par abonnement appliqués globalement (`JwtAuthenticationFilter`, `LectureSeuleInterceptor`). |
| Plafond de remise du caissier | Appliqué hors ligne | `encaisserHorsLigne` passe par `enregistrer` puis `exigerRemiseAutorisee` (`VenteService.java:360`). |
| Volumes | Bornés | 101 ventes → `409 "Envoyez au plus 100 ventes a la fois"`. Au plus 200 lignes par vente (`@Size`). Limiteur à 30 envois par minute et par utilisateur. |
| Concurrence | Aucune double vente | 3 salves de 4 requêtes simultanées (2 `POST /pdv/ventes` et 2 synchronisations, même clé) : une seule `pdv_vente` par clé en base. Les perdants reçoivent le même ticket, ou `[]` (erreur passagère `ObjectOptimisticLockingFailureException`, l'appareil renverra). |
| Heure de l'appareil | Bornée | `saisieLe = 2020-01-01` → vente enregistrée à l'ouverture de la session (`VenteService.enregistrer`). |
| Messages d'erreur | Sobres | Les `BusinessException` sont métier. Une erreur inattendue ne montre que le nom de classe (`HorsLignePdvService.java:313-316`). Pas de trace de pile. |
| XSS | Aucun | Aucun `[innerHTML]` ni `bypassSecurityTrust*` dans les nouveaux écrans. |

---

## Constats

### F-HL-1 : la reprise accepte d'un bloc tous les prix bas, alors que le conflit n'en montre qu'un

**Gravité** : Faible. CVSS 3.1 indicatif 3.1 (AV:N/AC:H/PR:L/UI:R/S:U/C:N/I:L/A:N). OWASP A04 (conception non sûre).

**Emplacement**
- `backend/src/main/java/tg/parcauto/api/pointdevente/vente/VenteService.java:143-147` : seule la première ligne fautive lève l'exception.
- `backend/src/main/java/tg/parcauto/api/pointdevente/horsligne/HorsLignePdvService.java:206-207` : `prixAccepte = true` pour toutes les lignes.
- `HorsLignePdvService.java:301-303` : le détail ne donne pas le prix du catalogue.

**Statut** : confirmé par revue de code. La reprise d'une ligne a été testée.

**Description**
1. Le message du conflit ne cite que la première ligne vendue sous le catalogue.
2. Le détail affiché au gérant (`articles`) donne « 1 x Fer de 16 a 10 F », sans le prix du catalogue.
3. La reprise rejoue la vente avec `prixAccepte = true` pour toutes les lignes.

**Scénario d'exploitation**
1. Un caissier enregistre hors ligne une vente de 3 lignes, dont 3 prix minorés.
2. Le gérant lit « Prix de X plus bas… », juge l'écart acceptable et clique sur Reprendre.
3. Les deux autres minorations passent avec lui, sans qu'il les ait vues.
4. Elles ne sont visibles qu'après coup, dans `ecartsPrix`.

**Correctif**
- Dans `encaisserHorsLigne`, ne pas lever d'exception à la première ligne. Accumuler toutes les lignes sous le catalogue, puis lever une seule `BusinessException` qui les liste toutes.
- Dans `VenteDto2`, renvoyer pour chaque ligne le prix encaissé et le prix du catalogue actuel.
- Côté écran, afficher le total de l'écart et demander une confirmation explicite.
- Optionnel : un paramètre `ecartMaxAccepte`, ou la confirmation ligne par ligne dans `Reprise`.

---

### F-HL-2 : le point de synchronisation, utilisable en ligne, contourne le contrôle de stock et la mise en vente

**Gravité** : Faible. CVSS 3.1 indicatif 3.5 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:L/A:N). OWASP A04.

**Emplacement**
- `VenteService.java:153` : `ligne(..., false)` vend un article retiré.
- `VenteService.java:194` : `exigerEnVente`.
- `VenteService.java:170` : `stockService.sortieConstatee`, sans refus.

**Statut** : confirmé par revue de code. L'enregistrement par la synchronisation en pleine connexion a été testé (ventes 129 et 130).

**Description**
Le serveur ne vérifie pas que l'appareil était réellement hors ligne. `POST /api/pdv/hors-ligne/synchronisation` est un second chemin d'encaissement, toujours ouvert au caissier. Par rapport à `POST /api/pdv/ventes`, il permet de :
1. Vendre au-delà du stock disponible. Le stock tombe à 0 et le manque est seulement « signalé ».
2. Vendre un article désactivé par le gérant : produit retiré, rappelé, prix en révision.
3. Échapper à l'impression des bons de cuisine. Les prix restent contrôlés (F-HL-1 à part).

**Scénario d'exploitation**
Un caissier vend un article retiré, ou réservé à un client, en passant par la synchronisation. Le stock du magasin est mis à zéro. Le contrôle ne repose plus que sur la lecture des « manques » par le gérant.

**Correctif**
- Rejeter, ou passer en conflit, les ventes hors ligne dont `saisieLe` est postérieure de moins de N secondes à la dernière requête en ligne réussie du même `sid`. Plus simplement : passer en conflit toute vente qui porte sur un article désactivé ou en manque de stock, au lieu de l'enregistrer.
- Au minimum, envoyer une alerte `VENTE_HORS_LIGNE` aussi pour les ventes enregistrées avec `manques`, et pas seulement pour les conflits (`VeilleAlertes.ventesHorsLigne` ne lit que `statut = 'CONFLIT'`).
- Ajouter un réglage « vente hors ligne autorisée » par caisse ou par profil.

---

### F-HL-3 : conflits stockés sans borne, contenu complet compris

**Gravité** : Faible. CVSS 3.1 indicatif 3.5 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:N/A:L). OWASP A04, consommation de ressources.

**Emplacement**
- `HorsLignePdvService.java:104-105` : une vente invalide est enregistrée en conflit.
- `HorsLignePdvService.java:151-153`.
- `HorsLignePdvService.java:248-250` : `contenu` = JSON complet.
- `HorsLignePdvService.java:41` et `:75` : 100 ventes par envoi, 30 envois par minute.
- `backend/src/main/java/tg/parcauto/api/config/PlafondEnvois.java:38` : un corps JSON envoyé en morceaux, sans `Content-Length`, n'est pas plafonné. Ce point est antérieur à ces changements.

**Statut** : confirmé par revue de code. Une vente mal formée gardée en conflit a été testée (trace 16, `"Vente incomplete : numeroProvisoire ne doit pas etre vide"`). Pas de test de volume (DoS interdit).

**Description**
Chaque vente reçue avec une clé nouvelle est conservée, même mal formée : jusqu'à 200 lignes avec des notes de 255 caractères, environ 80 ko de `contenu`. Le plafond théorique pour un seul compte caissier est de 30 × 100 = 3 000 conflits par minute, soit plusieurs centaines de Mo par heure. S'y ajoutent les notifications « vente hors ligne à traiter » et une liste du gérant noyée sous le bruit.

**Correctif**
- Ne garder en base qu'un résumé des ventes mal formées. Ne garder le contenu complet que pour les ventes valides en conflit métier.
- Ajouter un plafond de conflits ouverts par caissier, par exemple 200. Au-delà : refus en 429 et alerte au gérant.
- Faire porter le limiteur sur le nombre de ventes, par exemple 300 ventes par minute, et non sur le nombre d'envois.
- `PlafondEnvois` : refuser aussi les corps JSON sans longueur annoncée au-delà du plafond. Par exemple, envelopper le flux dans un `BoundedInputStream`, ou régler `server.tomcat.max-swallow-size` et un `ContentCachingRequestWrapper` borné.

---

### F-HL-4 : copie du catalogue (prix d'achat) et résultats laissés dans le navigateur après déconnexion

**Gravité** : Faible. CVSS 3.1 indicatif 2.4 (AV:P/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N). OWASP A01, A04.

**Emplacement**
- `frontend/src/app/core/services/pdv-hors-ligne.service.ts:219-224` : `pdv.copie.<id>.catalogue`.
- `frontend/src/app/pages/pdv/caisse/caisse.ts:300`.
- `frontend/src/app/core/auth/auth.service.ts:246` : à la déconnexion, seuls `pin.empreinte.*` et `agences.copie.*` sont effacés.
- `backend/src/main/java/tg/parcauto/api/pointdevente/catalogue/CatalogueDto.java:83` : `prixAchat` est renvoyé au gérant et à l'administrateur.

**Statut** : confirmé par revue de code.

**Description**
Quand un gérant ou un administrateur ouvre la caisse, la réponse de `/api/pdv/articles/caisse` contient `prixAchat`, donc les marges. Elle est copiée en clair dans `localStorage` et y reste après la déconnexion. C'est aussi le cas de `pdv.copie.<id>.societe` et `pdv.horsligne.resultats.<id>`. Sur un poste de caisse partagé, le caissier suivant les lit avec les outils du navigateur. Or les marges lui sont expressément cachées par le serveur.

**Correctif**
- Ne jamais mettre `prixAchat` dans la copie hors ligne : le retirer avant `ecrire()`, ou utiliser un DTO caisse dédié sans prix d'achat.
- À la déconnexion, effacer aussi `pdv.copie.*` et `pdv.horsligne.resultats.*`. Comme pour les agences, ne garder que `pdv.horsligne.file.*`, les ventes non envoyées.

---

### I-HL-1 : idempotence par clé non liée au caissier ni à la session

**Gravité** : Info. OWASP A01.

**Emplacement**
- `VenteService.java:72-78` : `encaisser` rend le ticket de la vente qui porte la clé.
- `VenteService.java:67-68` : `ticketParCle`.
- `HorsLignePdvService.java:143-148` : `dejaTraitee`.

**Statut** : confirmé par revue de code.

**Description**
Un utilisateur de la même entreprise qui envoie une clé déjà utilisée reçoit le ticket complet, ou le résultat, de cette vente : lignes, client, montants. Peu importe le caissier ou la session. La règle « chaque caisse ne voit que ses opérations » peut donc être contournée par qui connaît la clé. Les clés sont des UUID v4, que seuls le gérant (liste hors ligne) et l'appareil voient : l'exploitation est très improbable.

**Correctif**
Dans `encaisser` et `dejaTraitee`, ne rendre la vente existante que si elle a été faite par le même utilisateur, ou sur la même session. Sinon, refuser en 409 « clé déjà utilisée ».

---

### I-HL-2 : attente active d'une seconde dans le fil de la requête

**Gravité** : Info.

**Emplacement** : `backend/src/main/java/tg/parcauto/api/pointdevente/vente/VenteController.java:45-75`.

**Description**
Toute `DataIntegrityViolationException` ou `ConcurrencyFailureException` d'un encaissement qui porte une clé déclenche jusqu'à 5 × 200 ms de `Thread.sleep`. Cela vaut même quand la cause n'est pas un doublon de clé, par exemple deux ventes différentes en course sur le même stock. Effet limité, l'utilisateur est authentifié.

**Correctif** : ne faire l'attente que si la cause est la violation de `ux_pdv_vente_cle_hors_ligne`, par exemple en testant le nom de contrainte dans `ConstraintViolationException`.

---

### I-HL-3 : contrôles côté navigateur seulement

**Gravité** : Info.

**Constats**
- `caisseBoutiqueGuard` (`auth.guard.ts:53-62`) ouvre la caisse simple au restaurant si `?horsLigne` est présent et que le navigateur est hors ligne. C'est un choix d'écran, sans effet de sécurité : le serveur accepte de toute façon l'encaissement du profil.
- `nouvelleCle()` (`pdv-hors-ligne.service.ts:148-151`) se replie sur `Date.now() + Math.random()` quand `crypto.randomUUID` est absent, c'est-à-dire en HTTP non sécurisé sur un réseau local. Une collision, très improbable, ferait passer une vente pour un renvoi : elle serait perdue sans bruit.

**Correctif** : utiliser `crypto.getRandomValues`, disponible hors contexte sécurisé, pour fabriquer un UUID v4.
