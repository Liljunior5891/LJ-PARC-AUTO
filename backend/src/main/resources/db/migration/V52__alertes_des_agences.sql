-- =============================================================================
-- V52 : seuils des alertes des agences.
--
-- Chaque compte peut avoir un seuil d'alerte : en dessous, l'alerte « cash
-- insuffisant » ou « virtuel insuffisant » est levee. Le delai d'alerte avant
-- l'echeance d'un credit se regle avec les autres reglages des agences.
-- =============================================================================

ALTER TABLE agc_compte ADD COLUMN seuil_alerte BIGINT;
ALTER TABLE agc_compte ADD CONSTRAINT ck_agc_compte_seuil CHECK (seuil_alerte IS NULL OR seuil_alerte >= 0);

ALTER TABLE agc_reglage ADD COLUMN jours_alerte_echeance INTEGER NOT NULL DEFAULT 7;
ALTER TABLE agc_reglage ADD CONSTRAINT ck_agc_reglage_echeance CHECK (jours_alerte_echeance BETWEEN 0 AND 90);
