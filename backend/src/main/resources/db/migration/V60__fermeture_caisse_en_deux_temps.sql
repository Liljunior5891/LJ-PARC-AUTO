-- Fermeture de la caisse d'un caissier en deux temps : le caissier compte son tiroir
-- et soumet (statut SOUMISE, plus aucune operation), le superviseur valide ou rejette.
ALTER TABLE agc_session_caisse DROP CONSTRAINT IF EXISTS ck_agc_session_statut;
ALTER TABLE agc_session_caisse ADD CONSTRAINT ck_agc_session_statut CHECK (statut IN ('OUVERTE', 'SOUMISE', 'FERMEE'));

-- Une seule caisse en cours (ouverte ou soumise) par caissier.
DROP INDEX IF EXISTS uk_agc_session_ouverte;
CREATE UNIQUE INDEX uk_agc_session_ouverte ON agc_session_caisse (caissier_id) WHERE statut IN ('OUVERTE', 'SOUMISE');

ALTER TABLE agc_session_caisse
    ADD COLUMN IF NOT EXISTS soumise_le            TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS montant_soumis        BIGINT,
    ADD COLUMN IF NOT EXISTS ecart_soumis          BIGINT,
    ADD COLUMN IF NOT EXISTS justification_soumise VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS billettage_soumis     TEXT,
    ADD COLUMN IF NOT EXISTS motif_rejet           VARCHAR(500),
    ADD COLUMN IF NOT EXISTS rejetee_le            TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejetee_par_nom       VARCHAR(150);
