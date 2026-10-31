-- =============================================================================
-- V20 : code PIN personnel.
--
-- Il deverrouille l'ecran de caisse d'un caissier deja connecte (au debut du
-- service, apres une absence ou une pause). Stocke chiffre, comme le mot de
-- passe ; le gerant le definit ou le reinitialise, le caissier cree le sien a
-- la premiere ouverture s'il n'en a pas.
-- =============================================================================

ALTER TABLE utilisateur ADD COLUMN pin_hash VARCHAR(100);
