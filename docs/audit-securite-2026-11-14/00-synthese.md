# Audit sécurité du 2026-11-14 : synthèse

**Projet** : Nova, branche `saas`.

**Périmètre**
- Le travail fait depuis l'audit du 2026-11-11 (corrigé au commit 3d2ee34).
- D'abord les changements non commités :
  - vente hors ligne du point de vente (V88, V90) ;
  - notifications WhatsApp par WAHA (V89) ;
  - renommage Nova ;
  - refonte de plusieurs écrans.

**Méthode**
1. Revue statique.
2. Tests dynamiques sur une API de test : jar `parcauto-api-v98-revue.jar`, port 8095, base copiée `parcauto_e2e`. Elle a été arrêtée à la fin.

**Ce qui n'a pas été touché**
- L'API 8085 et la base `parcauto_saas`.
- Aucun compte WhatsApp n'a été lié, aucun message envoyé.
- Le réglage WAHA n'a pas été modifié.

**Traces laissées dans `parcauto_e2e`**
- Traces hors ligne 12 à 23.
- Ventes de test 129, 130, 131, 135, 139 et 143 : annulées.
- Conflits : abandonnés avec la note « Essai d'audit de securite ».
- Module PDV de l'entreprise 15 : activé puis désactivé.
- Entrées de journal liées à ces opérations.

## Rapports

| Fichier | Sujet |
|---|---|
| `01-vente-hors-ligne-pdv.md` | Vente hors ligne du PDV : cloisonnement, rejeu, prix, droits, volumes, concurrence |
| `02-notifications-whatsapp-waha.md` | WAHA : SSRF, clé d'API, injection, limites, veille, infrastructure |
| `03-renommage-nova.md` | Émetteur JWT, clés `localStorage`, écran de chargement, service worker |
| `04-ecrans-synchronisation-onglets.md` | Synchronisation entre onglets, pages refaites |

## Constats par gravité

| Gravité | Nombre | Constats |
|---|---|---|
| Critique | 0 | |
| Élevée | 0 | |
| Moyenne | 3 | M-WA-1, M-WA-2, M-WA-3 |
| Faible | 7 | F-HL-1, F-HL-2, F-HL-3, F-HL-4, F-WA-1, F-WA-2, F-WA-3 |
| Info | 6 | I-HL-1, I-HL-2, I-HL-3, I-WA-1, I-NOVA-1, plus une remarque sur la CSP et `index.html` |

### Moyenne

| Id | Titre | Emplacement | Statut |
|---|---|---|---|
| M-WA-1 | Changer l'URL WAHA envoie la clé enregistrée vers la nouvelle adresse ; SSRF avec 200 caractères de réponse renvoyés | `WhatsAppPlateformeController.java:256-284`, `WhatsAppWaha.java:174-201` | Confirmé (code) |
| M-WA-2 | Clé WAHA en clair en base, identique aux mots de passe du tableau de bord et du Swagger WAHA | `V89__notifications_whatsapp.sql:10`, `docker-compose.yml` | Confirmé (base et conteneur) |
| M-WA-3 | Numéro WhatsApp jamais vérifié : spam possible depuis le numéro de la plateforme, risque de bannissement, alertes vers un numéro non confirmé | `AlertesEntreprise.java:72-82`, `PreferencesNotificationController.java:98-115`, `InscriptionService.java:60,193` | Confirmé (code) |

### Faible

| Id | Titre | Emplacement |
|---|---|---|
| F-HL-1 | La reprise accepte tous les prix bas d'une vente, mais le conflit n'en montre qu'un | `VenteService.java:143-147`, `HorsLignePdvService.java:206-207,301-303` |
| F-HL-2 | La synchronisation, utilisable en ligne, contourne le stock et la mise en vente | `VenteService.java:153,170,194` |
| F-HL-3 | Conflits stockés sans borne, JSON complet compris ; corps JSON envoyé en morceaux non plafonné | `HorsLignePdvService.java:41,75,104,151,248`, `PlafondEnvois.java:38` |
| F-HL-4 | Catalogue copié avec les prix d'achat et laissé dans `localStorage` après déconnexion | `pdv-hors-ligne.service.ts:219-224`, `auth.service.ts:246`, `CatalogueDto.java:83` |
| F-WA-1 | Nom saisi par un client public repris tel quel dans les WhatsApp et e-mails (hameçonnage) | `VeilleAlertes.java:209-235`, `MarketplaceDto.java:159-162` |
| F-WA-2 | Essai super-admin sans limite ; pause synchronisée jusqu'à 60 s dans le fil HTTP | `WhatsAppPlateformeController.java:329-334`, `WhatsAppWaha.java:144-172` |
| F-WA-3 | Image `waha:latest` non épinglée ; tableau de bord et Swagger actifs ; WAHA absent du guide de mise en production | `docker-compose.yml` |

### Info

| Id | Titre |
|---|---|
| I-HL-1 | Idempotence par clé non liée au caissier : qui connaît la clé (UUID) lit le ticket d'une autre caisse de la même entreprise |
| I-HL-2 | Attente active d'une seconde sur toute violation d'intégrité d'un encaissement avec clé |
| I-HL-3 | `caisseBoutiqueGuard` purement côté navigateur ; repli `Math.random` pour la clé hors contexte sécurisé |
| I-WA-1 | Texte complet des notifications conservé sans durée de vie |
| I-NOVA-1 | Ancien émetteur JWT toléré sans date de fin. Sûr en l'état : même clé, expiration 8 h, `ver`, couples iss/aud stricts, jeton de double authentification non réutilisable |

## Les 3 priorités

1. **M-WA-1 et M-WA-2 : protéger la clé WAHA.**
   - Exiger la ressaisie de la clé à chaque changement d'URL.
   - Restreindre les hôtes autorisés et couper le suivi des redirections.
   - Ne plus renvoyer le corps des erreurs.
   - Sortir la clé de la base : variable d'environnement ou chiffrement.
   - Donner au tableau de bord et au Swagger WAHA un mot de passe distinct, ou les couper en production.
2. **M-WA-3 : confirmer le numéro des notifications** par un code WhatsApp avant tout envoi. Ajouter un plafond quotidien de messages par entreprise et limiter les essais. C'est ce qui protège le numéro unique de la plateforme contre un bannissement.
3. **F-HL-1 et F-HL-2 : durcir le contrôle du gérant sur les ventes hors ligne.**
   - Lister tous les écarts de prix dans le conflit, avec le prix du catalogue.
   - Passer en conflit, plutôt qu'enregistrer, les ventes d'articles retirés ou en manque de stock.
   - Alerter aussi sur les ventes enregistrées avec manques.
   - Ensuite, F-HL-4 : ne pas copier `prixAchat` et purger les copies à la déconnexion.

## État des surfaces

| Surface | État |
|---|---|
| Authentification | **Bon.** La tolérance de l'ancien émetteur JWT est sûre (I-NOVA-1). Le verrou d'écran et la double authentification ne sont pas affectés. |
| Autorisation et cloisonnement | **Bon.** Testé entre les entreprises 8 et 15 : liste, nombre, reprise, abandon, rejeu de clé et article d'une autre entreprise sont tous refusés ou isolés. Les droits `PDV_CAISSE` et `PDV_GESTION` sont conformes. Les préférences et la veille JDBC sont correctement attribuées par `entreprise_id`. Réserve I-HL-1, à l'intérieur d'une même entreprise. |
| Argent et caisses | **Correct, avec réserves.** Un prix bas passe en conflit, le plafond de remise est appliqué et le rejeu est idempotent. Aucune double vente sous concurrence (3 salves de 4 envois simultanés). Réserves F-HL-1 et F-HL-2. |
| Points publics | **Correct.** Pas de nouveau point public. Le contenu public est repris dans les notifications sans assainissement (F-WA-1). |
| Injection | **Bon.** Pas d'injection SQL : JDBC paramétré, JPQL. Pas d'injection d'URI ni d'en-tête vers WAHA, et pas de XSS dans les nouveaux écrans. Reste l'injection de contenu dans les messages (F-WA-1). |
| Appels sortants (SSRF) | **À durcir** (M-WA-1). |
| Secrets | **À durcir** (M-WA-2). `.env` est ignoré par git et `WAHA_API_KEY` est obligatoire dans le compose. |
| Infrastructure | **Correct en local** : WAHA publié sur 127.0.0.1 seulement et 401 sans clé. Pour la production : épinglage de l'image, tableau de bord à couper, guide à compléter (F-WA-3). |

## Ce qui reste hors périmètre ou à vérifier

**À vérifier : renvoi de `X-Api-Key` après redirection.** `SimpleClientHttpRequestFactory` (`HttpURLConnection`) renvoie-t-il l'en-tête quand il suit une redirection GET vers un autre hôte ? Si oui, M-WA-1 s'aggrave. Le correctif proposé, couper les redirections, règle la question dans les deux cas.

**Commits depuis 3d2ee34 survolés seulement.** Ils ont fait l'objet de leurs propres revues :
- PayGate : le rappel public revérifie l'état auprès de PayGate, ce qui est le bon schéma ;
- double authentification ;
- vérification de l'adresse e-mail ;
- jeton GPS par traceur ;
- fonds de réserve ;
- maintenance.

**Points antérieurs toujours ouverts**, rappelés sans être recomptés : absence de CSP et d'en-têtes `Referrer-Policy` / `Permissions-Policy`, jeton en `localStorage`.
