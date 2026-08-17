-- Reduction des profils a deux : ADMIN et GESTIONNAIRE_PARC.
--
-- Les profils de gestion (direction, comptabilite, logistique) se rejoignent
-- dans GESTIONNAIRE_PARC. Le profil CHAUFFEUR disparait avec l'application
-- mobile qui le motivait : les comptes concernes sont desactives plutot que
-- promus, pour qu'aucun droit ne soit accorde par accident.

-- ON CONFLICT : un utilisateur cumulant deux anciens profils convergerait
-- vers la meme ligne, ce que la cle primaire refuse.
INSERT INTO utilisateur_role (utilisateur_id, role)
SELECT DISTINCT utilisateur_id, 'GESTIONNAIRE_PARC'
FROM utilisateur_role
WHERE role IN ('DIRECTION', 'COMPTABLE', 'LOGISTIQUE')
ON CONFLICT DO NOTHING;

DELETE FROM utilisateur_role
WHERE role IN ('DIRECTION', 'COMPTABLE', 'LOGISTIQUE', 'CHAUFFEUR');

-- Un compte sans profil ne peut plus rien faire : on le desactive explicitement.
UPDATE utilisateur
SET actif = FALSE
WHERE id NOT IN (SELECT utilisateur_id FROM utilisateur_role);

ALTER TABLE utilisateur_role
    ADD CONSTRAINT ck_utilisateur_role_valeur
        CHECK (role IN ('ADMIN', 'GESTIONNAIRE_PARC'));
