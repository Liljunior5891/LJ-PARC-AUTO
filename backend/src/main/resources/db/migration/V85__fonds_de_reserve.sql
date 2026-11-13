-- =============================================================================
-- V85 : fonds de reserve du point de vente (option desactivee par defaut).
-- Active, il recoit en continu un pourcentage du benefice net (marge brute -
-- depenses, avant les parts des associes) ; c'est un compte separe : la dotation
-- quitte le compte de l'entreprise. Les depenses peuvent etre payees par le fonds,
-- dans la limite de son solde (celles du gerant attendent l'administrateur).
-- =============================================================================

ALTER TABLE pdv_reglage ADD COLUMN fonds_reserve_actif BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pdv_reglage ADD COLUMN fonds_reserve_pourcent NUMERIC(5, 2) NOT NULL DEFAULT 10;
ALTER TABLE pdv_reglage ADD CONSTRAINT ck_pdv_reglage_fonds_reserve
    CHECK (fonds_reserve_pourcent > 0 AND fonds_reserve_pourcent <= 100);

-- Periodes d'application du pourcentage : une activation ou un changement de taux
-- ouvre une periode (a partir du jour meme), une desactivation ferme la courante.
-- Les jours deja passes gardent ainsi le taux qui s'appliquait alors.
CREATE TABLE pdv_fonds_reserve_periode (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    debut          DATE NOT NULL,
    fin            DATE,
    pourcent       NUMERIC(5, 2) NOT NULL,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT ck_pdv_fonds_reserve_periode_dates CHECK (fin IS NULL OR fin >= debut),
    CONSTRAINT ck_pdv_fonds_reserve_periode_pourcent CHECK (pourcent > 0 AND pourcent <= 100)
);
CREATE INDEX idx_pdv_fonds_reserve_periode ON pdv_fonds_reserve_periode (entreprise_id, debut);
-- Une seule periode ouverte par entreprise.
CREATE UNIQUE INDEX uk_pdv_fonds_reserve_periode_ouverte ON pdv_fonds_reserve_periode (entreprise_id) WHERE fin IS NULL;

-- Le fonds de reserve devient un moyen de payer une depense.
ALTER TABLE pdv_depense DROP CONSTRAINT ck_pdv_depense_mode;
ALTER TABLE pdv_depense ADD CONSTRAINT ck_pdv_depense_mode
    CHECK (mode IN ('CAISSE', 'COFFRE', 'BANQUE', 'RESERVE', 'ESPECES', 'MOBILE_MONEY'));
