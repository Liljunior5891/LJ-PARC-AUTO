# Audit sécurité 2026-11-14, sujet 2 : notifications WhatsApp par WAHA (V89)

Date : 2026-11-14. Branche `saas`, changements non commités.

## Périmètre

**Backend**
- `common/notification/` : `WhatsAppWaha`, `NotificationService`, `AlertesEntreprise`, `VeilleAlertes`, `PreferencesNotificationController`, `TypeAlerte`.
- `plateforme/notification/WhatsAppPlateformeController`.
- `V89__notifications_whatsapp.sql`.

**Infrastructure** : service `waha` de `docker-compose.yml`, conteneur `nova-waha`.

## Méthode

1. Revue statique.
2. Tests en lecture sur l'API de test (port 8095, base `parcauto_e2e`) et sur le conteneur WAHA local.

**Ce qui n'a pas été fait**
- Le réglage WAHA n'a pas été modifié : une modification écrit dans le journal de la plateforme.
- Aucun compte WhatsApp n'a été lié.
- Aucun message n'a été envoyé.

Dans `parcauto_e2e`, `plateforme_whatsapp.actif = false` : la veille de l'API de test n'a rien pu envoyer.

## Ce qui tient (confirmé)

| Contrôle | Résultat | Preuve |
|---|---|---|
| Clé d'API jamais renvoyée au navigateur | OK | `GET /api/plateforme/whatsapp` (super-admin) → `{"actif":false,"url":"http://127.0.0.1:3005","cleDefinie":true,"session":"default","pauseMs":1500}` |
| Clé absente du journal | OK | `WhatsAppPlateformeController.resume()` ne garde que actif, url, session et pause. La mise à jour passe en JDBC, hors `EcouteurAudit`. `log.warn` n'écrit que l'URL. |
| Accès console | OK | `/api/plateforme/**` exige `SUPER_ADMIN` (`SecurityConfig`) et `@PreAuthorize(PLATEFORME)`, avec la double authentification du super-admin. |
| Injection d'en-têtes ou d'URL vers WAHA | Aucune | `session` est contrôlé par `^[A-Za-z0-9_-]+$` et passé en variable d'URI encodée. `chatId` ne garde que les chiffres. Le corps est un JSON sérialisé par Jackson. |
| Préférences cloisonnées | OK | `ContexteEntreprise.exiger()` est appelé partout. Les requêtes JDBC sont paramétrées par `entreprise_id`. Rôle `ADMINISTRATION`. |
| Veille sur toutes les entreprises | OK | Chaque requête de `VeilleAlertes` prend `entreprise_id` dans la ligne lue, et les jointures portent sur des clés primaires. Le regroupement se fait par `entrepriseId`, et `alertes.envoyer` vise le numéro et les e-mails de cette entreprise : pas de mélange entre entreprises. Toutes les requêtes sont paramétrées, sans concaténation SQL. |
| Message d'essai de l'administrateur | Limité | Un par minute et par entreprise (`notif_alerte_envoyee`, clé `essai-<minute>`). |
| WAHA sans clé | Refusé | `curl 127.0.0.1:3005/api/sessions`, `/dashboard/`, `/api/server/version`, `/` → `401` |
| Port 3005 | Local | `docker inspect nova-waha` → `{"3000/tcp":[{"HostIp":"127.0.0.1","HostPort":"3005"}]}` |
| E-mail | OK | `MimeMessageHelper.setText(texte, false)` : texte brut, pas d'injection HTML. |

---

## Constats

### M-WA-1 : changer l'adresse WAHA envoie la clé d'API enregistrée vers la nouvelle adresse (SSRF à lecture partielle)

**Gravité** : Moyenne. CVSS 3.1 indicatif 4.4 (AV:N/AC:L/PR:H/UI:N/S:C/C:L/I:L/A:N). OWASP A10 (SSRF), A04.

**Emplacement**
- `backend/src/main/java/tg/parcauto/api/plateforme/notification/WhatsAppPlateformeController.java:256-262` : l'URL n'est contrôlée que par `^https?://[^\s]+$`.
- Même fichier, `:278-284` : `cle_api = coalesce(?, cle_api)`. Une clé vide garde l'ancienne, même si l'adresse change.
- `backend/src/main/java/tg/parcauto/api/common/notification/WhatsAppWaha.java:174-186` : `X-Api-Key` est ajoutée pour toute URL.
- `WhatsAppWaha.java:188-201` : jusqu'à 200 caractères du corps de la réponse sont renvoyés à l'écran (`"WAHA a repondu 404 : ..."`).
- `WhatsAppWaha.java:84-131` : `etatSession` et `codeQr` renvoient des champs du JSON reçu.

**Statut** : confirmé par revue de code. Non rejoué dynamiquement, pour ne pas écrire dans le journal de la plateforme.

**Description**
L'adresse du serveur WAHA est libre : `http://169.254.169.254/…`, `http://127.0.0.1:5434`, services internes, hôte public. La clé enregistrée part avec chaque appel, dans l'en-tête `X-Api-Key`. Avec `SimpleClientHttpRequestFactory`, les GET suivent les redirections ; le renvoi de l'en-tête après redirection reste à vérifier.

**Scénario d'exploitation**
1. Un attaquant obtient une session super-admin : vol de jeton, poste compromis, XSS future dans la console.
2. Il remplace l'URL par `https://attaquant.example`, sans toucher au champ de la clé.
3. Il clique sur « État de la session » : la clé WAHA lui parvient en clair.
4. Avec elle, il pilote le compte WhatsApp de la plateforme : envoi de messages au nom de Nova et, selon le moteur, lecture des conversations du numéro relié.
5. Le même réglage sert à sonder le réseau interne et à lire le début des réponses.

**Correctif**
1. Exiger la ressaisie de la clé dès que l'URL change. Côté serveur : si `url` diffère de l'URL enregistrée et que `cleApi` est vide, refuser en 400 ou effacer la clé.
2. Restreindre l'adresse :
   - liste blanche par configuration (`parcauto.whatsapp.hotes-autorises`, par défaut `127.0.0.1,waha`) ;
   - ou refus des adresses link-local, de métadonnées et privées autres que l'hôte WAHA attendu, en résolvant le nom avant l'appel.
3. Désactiver le suivi des redirections : `HttpURLConnection.setInstanceFollowRedirects(false)` via une sous-classe de `SimpleClientHttpRequestFactory`, ou `JdkClientHttpRequestFactory` avec `HttpClient.Redirect.NEVER`.
4. Ne pas renvoyer le corps des réponses d'erreur à l'écran : le journaliser côté serveur, tronqué, et n'afficher que le code HTTP.

---

### M-WA-2 : clé d'API WAHA stockée en clair en base, et réutilisée comme mot de passe du tableau de bord et du Swagger

**Gravité** : Moyenne. CVSS 3.1 indicatif 4.9 (AV:N/AC:H/PR:H/UI:N/S:C/C:H/I:L/A:N). OWASP A02 (défaillances cryptographiques), A05.

**Emplacement**
- `backend/src/main/resources/db/migration/V89__notifications_whatsapp.sql:10` : `cle_api VARCHAR(200)` en clair.
- `docker-compose.yml`, service `waha` : `WAHA_DASHBOARD_PASSWORD: ${WAHA_API_KEY}` et `WHATSAPP_SWAGGER_PASSWORD: ${WAHA_API_KEY}`, utilisateur `admin`.

**Statut** : confirmé.

**Preuve**
```
$ psql parcauto_e2e -c "select left(cle_api,3)||'***', length(cle_api) from plateforme_whatsapp"
037*** | 48
$ docker exec nova-waha printenv | grep -E '^WAHA|^WHATSAPP'   (masqué)
WAHA_API_KEY=037***
WAHA_DASHBOARD_PASSWORD=037***
WHATSAPP_SWAGGER_PASSWORD=037***
WAHA_DASHBOARD_USERNAME=adm***
```

**Description**
Une sauvegarde de la base, une réplique ou un accès en lecture SQL donne directement le contrôle du compte WhatsApp de la plateforme : envoi, et lecture des conversations avec le moteur NOWEB. La même valeur ouvre aussi le tableau de bord et le Swagger WAHA. Les sauvegardes `/tmp/avant_vXX.dump` mentionnées dans les notes d'exploitation contiendront la clé.

**Correctif**
- Préférer une variable d'environnement `WAHA_API_KEY`, lue par l'API comme les autres secrets. Ne plus la stocker en base : la console n'affiche que « clé définie par le serveur ».
- À défaut, chiffrer `cle_api` en AES-GCM avec une clé maîtresse d'environnement, contrôlée par `GardeSecrets` en production.
- Côté WAHA : `WAHA_API_KEY=sha512:<empreinte>`, géré nativement par WAHA, pour ne pas garder la clé en clair dans l'environnement du conteneur.
- Donner au tableau de bord et au Swagger un mot de passe distinct, ou les désactiver en production (`WAHA_DASHBOARD_ENABLED=false`, `WHATSAPP_SWAGGER_ENABLED=false`).
- Ajouter WAHA à `docs/mise-en-production.md` : rotation de la clé, désactivation du tableau de bord.

---

### M-WA-3 : numéro destinataire jamais vérifié, d'où un abus possible du numéro WhatsApp de la plateforme et des alertes vers un numéro non confirmé

**Gravité** : Moyenne. CVSS 3.1 indicatif 4.3 (AV:N/AC:L/PR:L/UI:N/S:C/C:N/I:N/A:L). OWASP A04.

**Emplacement**
- `backend/src/main/java/tg/parcauto/api/common/notification/AlertesEntreprise.java:72-82` : le numéro est `entreprise.telephone`, sinon le téléphone d'un administrateur.
- `backend/src/main/java/tg/parcauto/api/plateforme/inscription/InscriptionService.java:60,193` : téléphone libre à l'inscription. Seul l'e-mail est vérifié.
- `backend/src/main/java/tg/parcauto/api/parametrage/SocieteService.java:55,71` : téléphone modifiable à tout moment par l'administrateur.
- `backend/src/main/java/tg/parcauto/api/common/notification/PreferencesNotificationController.java:98-115` : essai, une fois par minute.
- `backend/src/main/java/tg/parcauto/api/common/notification/VeilleAlertes.java:87-100` : « Bienvenue » envoyé à l'inscription.

**Statut** : confirmé par revue de code. Aucun envoi testé.

**Description et scénarios**
1. **Abus du numéro de la plateforme.**
   - Un inscrit, avec une adresse e-mail jetable vérifiée, met le numéro d'un tiers comme téléphone de l'entreprise.
   - Le tiers reçoit « Bienvenue », puis un message d'essai par minute (60 par heure), et toutes les alertes activées par défaut.
   - Multiplié par plusieurs essais gratuits, cela suffit à faire signaler puis bannir le numéro WhatsApp unique de la plateforme. Toutes les entreprises perdent alors leurs notifications.
2. **Données vers un mauvais destinataire.**
   - Le numéro d'alerte est par défaut le téléphone public de la société, repris sur la boutique en ligne.
   - C'est souvent le téléphone du comptoir, tenu par les caissiers.
   - Les alertes « écarts de caisse », « validations » et éventuellement « résumé de la journée » (chiffre d'affaires, dépenses) y partent sans que l'administrateur ait confirmé ce numéro.

**Correctif**
- Créer un champ « numéro des notifications » distinct du téléphone public, confirmé par un code à 6 chiffres envoyé sur WhatsApp. Le code est à usage unique, valable 10 minutes, avec au plus 3 essais par heure.
- Aucun message autre que le code de confirmation tant que le numéro n'est pas confirmé. Revenir à l'état non confirmé à chaque changement.
- Ajouter un plafond global de messages WhatsApp par entreprise et par jour, par exemple 50, avec une alerte au super-admin au-delà.
- Pour l'essai : un message par heure et par entreprise suffit.

---

### F-WA-1 : contenu saisi par le public repris tel quel dans les notifications (hameçonnage au nom de Nova)

**Gravité** : Faible. CVSS 3.1 indicatif 3.5 (AV:N/AC:L/PR:N/UI:R/S:U/C:N/I:L/A:N). OWASP A03 (injection de contenu).

**Emplacement**
- `VeilleAlertes.java:209-215` : `client_nom` de la commande en ligne.
- `VeilleAlertes.java:216-222` : demande de location.
- `VeilleAlertes.java:229-235` : `alerte_gps.message`.
- `backend/src/main/java/tg/parcauto/api/marketplace/MarketplaceDto.java:159-162` : `nom` et `prenoms` limités à 100 caractères, sans filtre de contenu.
- `AlertesEntreprise.java:119-120` : texte WhatsApp formaté avec `*` et `_`.

**Statut** : confirmé par revue de code. Pas de commande publique de test créée.

**Description**
Le nom d'un acheteur sans compte peut contenir des sauts de ligne, du gras et de l'italique WhatsApp, et des liens. Par exemple : `Koffi\n\n*Nova : votre abonnement expire, réglez ici* https://…`. Il arrive tel quel dans le WhatsApp et l'e-mail envoyés à l'administrateur, signés « _Nova_ ». Le limiteur est de 10 commandes par heure et par adresse IP.

**Correctif**
Assainir les champs d'origine externe avant de les mettre dans un message :
- remplacer `\r`, `\n` et les caractères de contrôle par une espace ;
- retirer `* _ ~ \``` ;
- neutraliser les URL : refuser `://` et `www.` dans un nom, ou les afficher entre crochets ;
- tronquer à 60 caractères.

Le mieux est de centraliser ce traitement dans une fonction `AlertesEntreprise.champ(String)`, appelée par `VeilleAlertes`.

---

### F-WA-2 : message d'essai du super-admin sans limite, et pause d'envoi qui bloque le fil de la requête

**Gravité** : Faible. CVSS 3.1 indicatif 2.7 (AV:N/AC:L/PR:H/UI:N/S:U/C:N/I:N/A:L).

**Emplacement**
- `WhatsAppPlateformeController.java:329-334` : pas de limiteur.
- `WhatsAppWaha.java:144-155` : `essayer` envoie même si WhatsApp est désactivé.
- `WhatsAppWaha.java:162-172` : `espacer` est `synchronized` avec un `Thread.sleep` d'au plus 60 s, réglable.

**Description**
1. Le super-admin peut envoyer en boucle des messages d'essai vers n'importe quel numéro, avec le même risque de bannissement que M-WA-3.
2. L'essai partage la pause synchronisée avec la file des notifications. Avec `pauseMs = 60000`, chaque essai bloque le fil HTTP jusqu'à 60 s, et peut faire attendre la file derrière lui.

**Correctif**
- `limiteur.exiger("whatsapp-essai", 5, Duration.ofMinutes(10), …)`.
- Exiger que le numéro d'essai soit celui du compte relié, ou d'une liste configurée.
- Sortir l'essai du verrou `espacer`, ou plafonner `pauseMs` à 5 s.

---

### F-WA-3 : image WAHA non épinglée et tableau de bord activé

**Gravité** : Faible. OWASP A06 (composants vulnérables), A05.

**Emplacement** : `docker-compose.yml`, service `waha` : `image: devlikeapro/waha:latest`, tableau de bord et Swagger actifs par défaut (`WAHA_DASHBOARD_ENABLED=True`, `WHATSAPP_SWAGGER_ENABLED=True`, relevé dans le conteneur).

**Description**
- Une image `latest` change sans revue. C'est un composant tiers qui détient une session WhatsApp : risque de chaîne d'approvisionnement.
- Le tableau de bord est protégé par un Basic Auth `admin/<clé d'API>`. Il est seulement lié à 127.0.0.1, ce qui est bien, mais rien n'est prévu pour la production.

**Correctif**
- Épingler par version et par empreinte : `devlikeapro/waha:2026.10.1@sha256:…`.
- Désactiver le tableau de bord et le Swagger en production.
- Si l'API tourne en conteneur, utiliser un réseau Docker interne sans publication de port.
- Documenter le tout dans `docs/mise-en-production.md`.

---

### I-WA-1 : conservation des messages

**Gravité** : Info.

`notification_envoi.texte` garde en clair, sans durée de conservation, le texte complet des messages : chiffre d'affaires du jour, écarts, noms de clients.

**Correctif** : prévoir une purge, par exemple à 90 jours, ou masquer le texte après envoi.
