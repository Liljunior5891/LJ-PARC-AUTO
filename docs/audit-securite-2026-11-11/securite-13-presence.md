# Audit sécurité 13/13 : Gestion de présence (LJ SAAS, branche saas)

Périmètre : `backend/src/main/java/tg/parcauto/api/presence/` (PointagePublicController, AppairageBorneController, ContextePresenceInterceptor, SecuritePresence, PointageService, PresenceService, PresenceController, entités et dépôts), migration `V67__gestion_de_presence.sql`, écrans `frontend/src/app/pages/presence/**`.
Méthode : revue statique, puis sondes locales en lecture seule sur http://localhost:8085 (aucun pointage écrit, aucune force brute). Les constats déjà signalés (socle, plateforme, getRemoteAddr, etc.) ne sont repris que lorsqu'ils ont un effet propre à la présence.

Données locales au moment du test : 1 lieu (sans position GPS), 1 borne appairée, 1 employé.

---

## ÉLEVÉ

### E1. Pointage à distance : le code QR peut être relayé et la position GPS est déclarée par le client (confirmé par le code)
- Gravité : Élevée. CVSS indicatif 6.5 (AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:H/A:N). OWASP A04 (conception non sûre) / ASVS V11 (logique métier).
- Emplacements :
  - `backend/src/main/java/tg/parcauto/api/presence/SecuritePresence.java:29-31` (`PAS_SECONDES = 30`, `PAS_ACCEPTES = 6`) et `:109` (boucle `pas + 1` à `pas - 6`) : un code reste accepté environ 3 min 30 à 4 min.
  - `backend/src/main/java/tg/parcauto/api/presence/PointageService.java:80-94` : latitude, longitude et `precision` viennent du corps JSON, sans aucune preuve. La précision déclarée élargit le rayon jusqu'à 100 m (`:90-91`).
  - `PointageService.java:92-93` : le message d'erreur donne la distance exacte au lieu (« Vous êtes à N m »). Après un code valide, ce contrôle passe avant le PIN et ne consomme aucun essai : on dispose d'un oracle de trilatération illimité.
- Scénario : un collègue présent photographie la borne (ou copie l'URL `/pointage/{jeton}?c=XXXXXX`) et l'envoie par messagerie. L'absent, chez lui, ouvre l'URL dans les 3 minutes. Il simule la position avec les capteurs des outils de développement de Chrome, une application de « fausse position » Android ou un simple `curl`, avec les coordonnées du bureau et `precision:100`, puis tape son PIN. Le pointage est enregistré « à l'heure », en mode TELEPHONE, avec une distance de 0 m.
- Preuve (code) :
  ```java
  if (d.latitude() == null || d.longitude() == null) { ... }
  int distance = (int) Math.round(distanceMetres(lieu.getLatitude(), lieu.getLongitude(), d.latitude(), d.longitude()));
  int precision = d.precision() == null ? 0 : (int) Math.round(d.precision());
  if (distance > lieu.getRayonMetres() + Math.min(precision, MARGE_GPS_MAX)) { throw ... "Vous etes a " + distance + " m" ...
  ```
- Correctif :
  1. Réduire la fenêtre d'acceptation à `pas` et `pas - 1` (60 à 90 s au plus).
  2. Lier le téléphone à l'employé. Au premier pointage, l'appareil reçoit un cookie `HttpOnly` signé (jeton d'appareil stocké par empreinte). Les pointages suivants depuis un autre appareil sont refusés, ou mis « à valider » par le gérant.
  3. Ne plus renvoyer la distance exacte (« hors du périmètre » suffit). Plafonner la marge de précision, par exemple à 30 m, et refuser une précision absente ou nulle.
  4. Ajouter un réglage par lieu : « borne uniquement » ou « téléphone + adresse IP publique du site » (liste blanche de l'IP du Wi-Fi du bureau).
  5. Signaler les anomalies au tableau de bord : précision ronde ou nulle, coordonnées identiques d'un jour à l'autre, plusieurs employés depuis le même appareil.

### E2. Force brute du PIN : seul facteur, unique par entreprise, limite par appareil trop faible et remise à zéro (confirmé par le code)
- Gravité : Élevée. CVSS indicatif 6.5 (AV:N/AC:L/PR:L/UI:N/S:U/C:L/I:H/A:N). OWASP A07 (échecs d'authentification) / ASVS V2.2.1.
- Emplacements :
  - `SecuritePresence.java:33-38` : 10 essais faux par téléphone et 15 par borne dans une fenêtre de 10 min ; blocage de 10 min pour le téléphone et de 3 min seulement pour la borne.
  - `SecuritePresence.java:150-154` : au blocage, `e.essais.clear()` remet le compteur à zéro. Après 3 min, la borne accepte 15 nouveaux essais. Cela fait environ 5 essais par minute et par borne, soit environ 7 200 par jour, sans fin.
  - `PointagePublicController.java:120-122` : la clé du téléphone est `ip:` + `getRemoteAddr()`. Faire tourner les adresses (IPv6, proxys) contourne la limite.
  - `PointageService.java:153-165` : le PIN identifie seul l'employé, car il est unique dans l'entreprise (`PresenceService.java:205-216`, contrainte `uk_pres_employe_pin`). Tout PIN deviné ouvre donc le compte de quelqu'un : la probabilité de succès vaut E/10^6 par essai, E étant le nombre d'employés.
  - `PointagePublicController.java:61-66` et `:90-96` : l'historique par PIN n'écrit rien. Il permet une force brute discrète, qui ne crée aucun pointage visible et renvoie nom, matricule, fonction et l'historique du mois.
- Il n'existe aucun compteur global par entreprise ou par lieu, aucune alerte au gérant et aucun blocage progressif.
- Scénario : un porteur du jeton de borne (voir M1) lance un script depuis n'importe quelle IP contre `/api/public/presence/bornes/{jeton}/historique` au rythme de 15 essais toutes les 3 minutes. Avec 200 employés, il trouve en moyenne 1,4 PIN valide par jour et par borne. Il obtient l'identité, l'historique, puis la possibilité de pointer pour ces employés.
- Correctif :
  1. Ne pas vider le compteur au blocage. Bloquer de façon exponentielle : 3, 10, 30 minutes, puis 24 h.
  2. Ajouter un compteur global par entreprise et par lieu (par exemple 50 PIN faux par heure, puis blocage de la saisie et notification du gérant), en base ou dans un cache partagé.
  3. Exiger un identifiant en plus du PIN (matricule ou sélection du nom), afin que la probabilité de succès soit 1/10^6 et non E/10^6. Sinon, porter le PIN à 8 chiffres.
  4. Imposer aussi un code QR valide pour l'historique sur borne, ou le limiter à 3 essais.
  5. Journaliser les rafales de PIN faux.

---

## MOYEN

### M1. Jeton de borne : porteur permanent, non lié à l'appareil, utilisable de partout (confirmé par le code)
- CVSS indicatif 5.4. OWASP A07 / ASVS V3.
- Emplacements :
  - `ContextePresenceInterceptor.java:51-52` : la borne est authentifiée par la seule empreinte SHA-256 du jeton, sans expiration, sans lien à une IP ni à un appareil.
  - `PointagePublicController.java:70-77` : `GET /bornes/{jeton}/qr` délivre à volonté le code du moment, avec le jeton public du lieu (`PointageService.java:211-212`).
  - `frontend/src/app/pages/presence/borne/borne.ts:81` : le jeton est stocké dans `localStorage` sous `lj-borne-presence`, sur la même origine que l'application authentifiée. Toute XSS de l'application le lit.
- Scénario : un employé ayant un accès physique à la tablette copie le jeton (débogage distant Chrome, onglet non verrouillé). Depuis chez lui, il obtient le QR en continu et pointe en mode téléphone avec une position forgée (E1). Il peut aussi pointer en mode borne sans GPS pour tout PIN connu, et faire de la force brute (E2).
- Points positifs : la révocation existe (`reappairer`, `basculerBorne` effacent l'empreinte) et le jeton fait 128 bits aléatoires.
- Correctif :
  1. Faire tourner le jeton (par exemple chaque jour : le serveur renvoie un nouveau jeton dans la réponse `/qr`).
  2. Ajouter un réglage « IP autorisées » par borne.
  3. Afficher au gérant l'IP et le user-agent du dernier contact, et alerter sur un changement d'IP.
  4. Servir la borne sur une origine ou un sous-domaine distinct, ou stocker le jeton dans un cookie `HttpOnly; SameSite=Strict` dont le chemin se limite à `/api/public/presence/bornes`.

### M2. Le compteur d'essais par getRemoteAddr permet de bloquer le pointage de tout le monde (confirmé par le code ; l'effet en production dépend du proxy, à vérifier)
- CVSS indicatif 5.3 (A:L à A:H selon le déploiement). OWASP A04. C'est l'effet propre à la présence du problème déjà connu des limiteurs basés sur `getRemoteAddr`.
- Emplacements :
  - `PointagePublicController.java:120-122` (`"ip:" + getRemoteAddr()`).
  - `PointageService.java:168-174` : un code QR faux consomme un essai dans le même compteur que les PIN.
  - `AppairageBorneController.java:42-43` (`"appairage:" + getRemoteAddr()`).
  - `application.yml` ne définit pas `server.forward-headers-strategy`. Or `environment.prod.ts` (`apiUrl: '/api'`) laisse supposer un proxy inverse en production.
- Scénario :
  - Derrière un proxy inverse, toutes les requêtes ont la même adresse. N'importe qui connaissant un jeton de lieu (il figure en clair dans le QR affiché à l'entrée) envoie 10 codes faux. Le pointage par téléphone est alors bloqué 10 minutes pour toutes les entreprises de la plateforme, et l'attaque se renouvelle à volonté. De même, 10 codes d'appairage faux bloquent l'appairage de toutes les bornes.
  - Sans proxy, l'effet est le même pour tous les employés derrière le même NAT : le Wi-Fi du bureau, ou le CGNAT des opérateurs mobiles, très répandu.
  - Sur une borne, 15 PIN faux la bloquent 3 minutes pour tout le personnel.
- Correctif :
  1. Configurer `server.forward-headers-strategy=native` avec la liste des proxys de confiance (`server.tomcat.remoteip.internal-proxies`).
  2. Compter les échecs par couple (lieu, IP) plutôt que par IP seule.
  3. Ne pas compter un code QR expiré comme un PIN faux : utiliser un compteur séparé et plus large.
  4. Sur borne, préférer un délai croissant entre essais à un blocage complet.

### M3. Le responsable de lieu peut créer des employés fantômes, lire les PIN en clair et effacer des absences (confirmé par le code)
- CVSS indicatif 5.4 (PR:H, I:H). OWASP A01 (contrôle d'accès) / ASVS V4.1.3 (moindre privilège).
- Emplacements :
  - `PresenceController.java:40` : `GESTION` inclut `GESTIONNAIRE_PARC` et `SUPERVISEUR_AGENCE`. Les routes `:79` (créer), `:88` (modifier) et `:95` (réinitialiser le PIN) y sont ouvertes.
  - `PresenceService.java:124-131` et `:143-149` : le PIN en clair est renvoyé à l'appelant.
  - `PresenceService.java:158-167` : `dateEmbauche` et `actif` sont modifiables par le responsable.
  - `PointageService.java:320-326` et `:386` : un jour antérieur à `dateEmbauche`, ou postérieur à `desactiveLe`, devient `NON_EMBAUCHE` et disparaît des absences, du rapport du jour et des totaux du mois.
  - `PresenceService.java:64-66` : un employé est « visible » dès qu'un seul de ses lieux appartient au responsable, même s'il est aussi rattaché aux lieux d'autres responsables.
- Scénarios :
  1. Un gestionnaire de parc crée un employé fictif rattaché à son lieu, reçoit son PIN et le fait pointer chaque jour (fraude à la paie si les présences servent au salaire).
  2. Il réinitialise le PIN d'un collègue absent, lit le nouveau PIN et pointe à sa place. Le vrai employé découvre seulement que son PIN « ne marche plus ».
  3. Un responsable qui est lui-même employé de son lieu reporte sa `dateEmbauche` : toutes ses absences passées sont effacées des rapports. C'est la correction de ses propres heures demandée dans le périmètre de l'audit.
- Atténuation existante : les entités de présence sont tracées par `EcouteurAudit`, avec le PIN masqué.
- Correctif :
  1. Réserver la création d'employé, la réinitialisation du PIN, la modification de `dateEmbauche` et la désactivation à `ADMIN` et `GERANT`, ou les soumettre à leur validation.
  2. Interdire toute modification de sa propre fiche (employé dont `utilisateurId` = appelant).
  3. Remettre le PIN directement à l'employé (SMS ou affichage unique sur la borne après validation), plutôt que de le montrer au responsable.
  4. Rendre `dateEmbauche` non rétroactive au-delà des pointages et absences existants, ou garder l'historique des changements.

---

## FAIBLE

### F1. Appairage : codes à 6 chiffres cherchés dans toutes les entreprises (confirmé par le code)
- CVSS indicatif 3.7 (AC:H). OWASP A07.
- Emplacements :
  - `AppairageBorneController.java:45-48` : la requête porte sur `pres_borne`, toutes entreprises confondues.
  - `PresenceService.java:39` : le code est valable 15 min.
  - `AppairageBorneController.java:42-43` : la limite est de 10 essais par IP.
- Scénario : une énumération distribuée (plusieurs IP) frappe n'importe quel code en attente de la plateforme. L'attaquant récupère le jeton d'une borne d'une autre entreprise : nom de l'entreprise et du lieu, QR, force brute des PIN (E2). La vraie tablette reçoit alors « code inconnu ».
- Correctif :
  1. Code alphanumérique de 8 caractères (environ 10^12 combinaisons) ou durée de 5 min.
  2. Compteur global d'échecs d'appairage, avec alerte.
  3. Confirmation de la borne par le gérant après appairage (« borne X appairée depuis IP Y : confirmer ? »).

### F2. Le jeton public du lieu ne peut pas être renouvelé ; il révèle le nom de l'entreprise et du lieu (confirmé par un test)
- Emplacements :
  - `PresenceService.java:311-315` : `renouvelerSecret` ne change que `secretQr`, pas `jetonPublic`. Aucun point d'entrée ne régénère le jeton.
  - `PointageService.java:63-69` : la page d'accueil renvoie `lieu` et `entreprise`.
- Preuve (locale) :
  ```
  GET /api/public/presence/lieux/8868c8…(masqué)?c=123456
  -> 200 {"lieu":"resto","entreprise":"Maquis Le Palmier","codeValide":false,"positionExigee":true}
  ```
  Le jeton complet est aussi renvoyé dans le champ `path` des erreurs 409.
- Impact : un ancien employé ou un visiteur garde à vie un point d'entrée vers le lieu. Il s'en sert pour tester des codes (30 par 10 min), consommer les compteurs (M2) et, combiné à un code relayé, pour E1.
- Correctif : régénérer aussi `jetonPublic` dans `renouvelerSecret`, ou ajouter une action distincte.

### F3. Pointage sans traçabilité de l'appareil (confirmé par le code)
- Emplacements :
  - `PointagePresence.java:25-65` : ni identifiant de borne pour le mode BORNE, ni IP, ni user-agent, ni empreinte d'appareil.
  - `PointageService.java:123-132`.
- Impact : impossible d'enquêter sur une fraude E1 ou M1, par exemple pour repérer deux employés pointés depuis le même téléphone ou depuis une IP extérieure au site.
- Correctif : enregistrer `borne_id`, l'IP réelle (après correction de M2), un condensé du user-agent et l'identifiant d'appareil (E1, point 2), et les afficher sur la fiche du jour.

### F4. Les responsables voient tous les comptes de l'entreprise et peuvent relier n'importe lequel (confirmé par le code)
- Emplacements :
  - `PresenceController.java:102` et `PresenceService.java:223-234` : nom, e-mail, téléphone et rôles de tous les comptes actifs, ADMIN compris, sont exposés au rôle `GESTION`.
  - `PresenceService.java:168-177` : un responsable peut relier le compte de l'administrateur à un employé de son lieu, ce qui empêche de le relier à sa vraie fiche.
- Correctif : réserver ces deux points à `ADMIN`/`GERANT`, ou restreindre la liste aux comptes sans rôle d'administration.

### F5. La clé HMAC des PIN dérive du secret JWT (confirmé par le code)
- Emplacement : `SecuritePresence.java:45-46` et `:52-54`.
- Impact :
  - Changer le secret JWT (rotation, ou correction du secret de dev déjà signalée) invalide silencieusement tous les PIN.
  - Si le secret JWT fuit, ou reste celui de développement, une copie de la base suffit à retrouver chaque PIN en 10^6 calculs par entreprise.
- Correctif : utiliser une clé dédiée `parcauto.presence.cle-pin`, contrôlée par `GardeSecrets` en production, et prévoir une procédure de réémission des PIN.

### F6. Les codes d'appairage expirés ne sont jamais effacés, alors que l'index unique est global (confirmé par le code)
- Emplacements : `V67__gestion_de_presence.sql:89` (`uk_pres_borne_code` sur `code_appairage` pour toute la plateforme) et `PresenceService.java:393-396`. Un code expiré reste en base.
- Impact : avec le temps, une création ou un réappairage peut échouer sur un doublon (409) avec un code expiré d'une autre entreprise. C'est un problème de disponibilité mineur, sans fuite.
- Correctif : nettoyer `code_appairage` à l'expiration (tâche planifiée), ou utiliser un index partiel avec `code_expire_le > now()` couplé à un nouvel essai en cas de collision.

---

## INFO (pas de défaut exploitable)

- **Cloisonnement multi-tenant : correct.** L'intercepteur résout l'entreprise depuis le jeton (`ContextePresenceInterceptor.java:51-60`), puis `@TenantId` filtre employés, lieux et bornes. L'empreinte du PIN inclut `entrepriseId` (`SecuritePresence.java:53`). Un PIN d'une autre entreprise ne fonctionne donc pas, et les identifiants d'employé ou de lieu d'une autre entreprise donnent 404.
- **Secrets : générés correctement.** Jetons de 128 bits et secret QR de 160 bits, tirés de `SecureRandom`. Le QR est un TOTP HMAC-SHA1 à 6 chiffres et sa comparaison se fait en temps constant. La force brute du code QR est négligeable : 8 codes valides sur 10^6, limite de 30 essais par 10 min.
- **Rapports et exports.** Les rapports sont bornés (un jour, ou un mois par employé). Le module n'a pas d'export CSV, donc rien à neutraliser. Il n'existe aucun point d'entrée de correction manuelle des pointages (le mode `MANUEL` n'est pas exposé).
- **Mois invalide.** `PresenceController.java:62` appelle `YearMonth.parse` sans `try` : un mois invalide renvoie une erreur 500 générique au lieu d'une 400. Côté public, `mois()` gère correctement le cas.
- **XSS : pas d'injection possible.** `borne.ts:113` applique `bypassSecurityTrustHtml` au SVG du serveur. Ce SVG ne contient que des données de chemin (`CodeQr.java:36-38`), donc rien d'injectable aujourd'hui. Les autres écrans de présence utilisent l'interpolation Angular. Il serait plus robuste de rendre le QR via `<img [src]>` avec une URL `data:` validée.
- **Adresse interne.** `PointageService.joignable` remplace `localhost` par l'IP du serveur sur le réseau local et la renvoie au porteur du jeton de borne. Cela divulgue une adresse interne, sans gravité.
- **Déjà signalés, non repris :** le pointage et les bornes ignorent la suspension et la lecture seule ; les limiteurs se fondent sur `getRemoteAddr` (effet propre à la présence décrit en M2).

---

## Synthèse

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 2 (E1, E2) |
| Moyenne | 3 (M1, M2, M3) |
| Faible | 6 (F1 à F6) |

Trois priorités :
1. **E1** : réduire la fenêtre du QR, lier le téléphone à l'employé, ne plus renvoyer la distance, offrir l'option « borne seule » ou « IP du site ».
2. **E2 et M2** : blocage progressif sans remise à zéro, compteur global par entreprise et par lieu, identifiant en plus du PIN, `forward-headers-strategy` configuré.
3. **M3** : réserver PIN, création, `dateEmbauche` et désactivation à ADMIN/GERANT, et interdire la modification de sa propre fiche.

État des surfaces :
- **Authentification (PIN, bornes, appairage)** : à renforcer (E2, M1, F1, F5).
- **Autorisation et cloisonnement** : le cloisonnement entre entreprises est sain ; le moindre privilège entre rôles est insuffisant (M3, F4).
- **Intégrité du pointage** : c'est le point faible du module (E1, F3).
- **Points publics** : DoS possible par compteur partagé (M2) ; fuite mineure du nom de l'entreprise (F2).
- **Injection et XSS** : rien trouvé (requêtes paramétrées, pas de concaténation, SVG sûr).
- **Rapports et exports** : bornés, sans CSV.
