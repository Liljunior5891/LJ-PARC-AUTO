-- Le profil CHAUFFEUR est conserve : l'application mobile du chapitre 32
-- s'appuiera dessus. La contrainte posee en V8 le refusait.

ALTER TABLE utilisateur_role
    DROP CONSTRAINT IF EXISTS ck_utilisateur_role_valeur;

ALTER TABLE utilisateur_role
    ADD CONSTRAINT ck_utilisateur_role_valeur
        CHECK (role IN ('ADMIN', 'GESTIONNAIRE_PARC', 'CHAUFFEUR'));
