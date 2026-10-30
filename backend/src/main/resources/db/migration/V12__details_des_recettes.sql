-- =============================================================================
-- PARC AUTO - V12 : details d'une recette selon son type
-- -----------------------------------------------------------------------------
-- La saisie d'une recette s'adapte a son type : periode pour un transport, un
-- voyage ou un contrat, trajet et marchandise pour un transport ou une
-- livraison, kilometres pour un kilometrage supplementaire. Ces details etaient
-- jusqu'ici perdus dans le libelle ; ils ont desormais leurs colonnes.
-- Toutes sont facultatives : les recettes existantes restent valides.
-- =============================================================================

ALTER TABLE recette
    ADD COLUMN date_debut   DATE,
    ADD COLUMN date_fin     DATE,
    ADD COLUMN lieu_depart  VARCHAR(180),
    ADD COLUMN destination  VARCHAR(180),
    ADD COLUMN marchandise  VARCHAR(255),
    ADD COLUMN kilometres   BIGINT;

ALTER TABLE recette
    ADD CONSTRAINT recette_periode_coherente
        CHECK (date_debut IS NULL OR date_fin IS NULL OR date_fin >= date_debut),
    ADD CONSTRAINT recette_kilometres_positifs
        CHECK (kilometres IS NULL OR kilometres >= 0);
