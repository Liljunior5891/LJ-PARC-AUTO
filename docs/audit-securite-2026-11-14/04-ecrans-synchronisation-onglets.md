# Audit sécurité 2026-11-14, sujet 4 : synchronisation entre onglets et refonte des écrans

Date : 2026-11-14. Revue rapide : ce sont surtout des changements d'écran.

## Périmètre

- File hors ligne du point de vente partagée entre onglets : événement `storage` dans `core/services/pdv-hors-ligne.service.ts`.
- Pages refaites :
  - `pages/notifications/` ;
  - `pages/plateforme/whatsapp-plateforme.ts` ;
  - `pages/agences/services/*` ;
  - les autres pages agences, PDV et plateforme modifiées : agences-liste, audit, dépenses, rapprochement, soldes, transactions, journal, abonnements.

## Résultats

| Point | Statut | Détail |
|---|---|---|
| XSS | RAS | Aucun nouveau `[innerHTML]`, `bypassSecurityTrust*`, `document.write` ni `srcdoc`. Recherche sur tout `frontend/src/app` : seules des occurrences antérieures (accueil, borne, véhicule, rapports, ma-boutique). Les messages des conflits, des notifications et de WAHA sont rendus par interpolation Angular, donc échappés. |
| Code QR WhatsApp | RAS | L'image arrive en `data:` base 64 depuis l'API (`/api/plateforme/whatsapp/session/qr`). Elle est rendue sans contournement du filtre d'URL d'Angular. |
| File partagée entre onglets | RAS | La clé `pdv.horsligne.file.<idUtilisateur>` est propre à chaque utilisateur. L'événement `storage` ne vient que de la même origine. La file relue n'est que renvoyée au serveur, qui revérifie tout : prix, article, session, droits. Une file modifiée à la main dans le navigateur ne peut donc pas forcer un prix bas (voir sujet 1). La relecture avant écriture évite la perte de ventes entre onglets. |
| Changement d'utilisateur sur le même poste | RAS | `suivreUtilisateur()` recharge la file du nouvel utilisateur. Un résultat d'envoi arrivé après le changement d'utilisateur est ignoré (`proprietaire !== utilisateurCharge`). |
| Restes après déconnexion | Voir F-HL-4 (sujet 1) | `pdv.copie.*` (catalogue avec prix d'achat) et `pdv.horsligne.resultats.*` ne sont pas effacés. |
| Préférences des notifications | RAS | Les droits sont tenus côté serveur (`ADMINISTRATION`, entreprise du jeton). L'écran n'envoie que des couples type / canal. Un type inconnu est rejeté par la désérialisation de l'énumération. |
| Services des agences | RAS | Uniquement de la présentation : HTML, SCSS, et du TypeScript d'affichage. Aucun nouvel appel d'API. |

Aucun constat propre à ce sujet.
