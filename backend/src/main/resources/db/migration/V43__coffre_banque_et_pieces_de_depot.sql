-- =============================================================================
-- V43 : depenses et paiements fournisseurs payes depuis la caisse, le coffre du
-- gerant ou la banque (compte de l'entreprise) ; degagement du coffre = depot en
-- banque, atteste par un bordereau ou un releve joint, verifie par l'administrateur.
-- =============================================================================

-- Le coffre devient un moyen de paiement ; especes hors caisse et mobile money
-- restent permis pour les pieces deja enregistrees.
ALTER TABLE pdv_depense DROP CONSTRAINT ck_pdv_depense_mode;
ALTER TABLE pdv_depense ADD CONSTRAINT ck_pdv_depense_mode
    CHECK (mode IN ('CAISSE', 'COFFRE', 'BANQUE', 'ESPECES', 'MOBILE_MONEY'));
ALTER TABLE pdv_depense ADD COLUMN coffre_id BIGINT;
CREATE INDEX idx_pdv_depense_coffre ON pdv_depense (coffre_id) WHERE coffre_id IS NOT NULL;

ALTER TABLE pdv_reglement_fournisseur DROP CONSTRAINT ck_pdv_reglement_frs_mode;
ALTER TABLE pdv_reglement_fournisseur ADD CONSTRAINT ck_pdv_reglement_frs_mode
    CHECK (mode IN ('CAISSE', 'COFFRE', 'BANQUE', 'ESPECES', 'MOBILE_MONEY'));
ALTER TABLE pdv_reglement_fournisseur ADD COLUMN coffre_id BIGINT;
CREATE INDEX idx_pdv_reglement_frs_coffre ON pdv_reglement_fournisseur (coffre_id) WHERE coffre_id IS NOT NULL;

-- Depot en banque du degagement : banque, reference et date du bordereau, piece jointe.
ALTER TABLE pdv_degagement
    ADD COLUMN banque         VARCHAR(100),
    ADD COLUMN reference_depot VARCHAR(80),
    ADD COLUMN date_depot     DATE,
    ADD COLUMN piece_chemin   VARCHAR(255),
    ADD COLUMN piece_nom      VARCHAR(255),
    ADD COLUMN piece_type     VARCHAR(100);
