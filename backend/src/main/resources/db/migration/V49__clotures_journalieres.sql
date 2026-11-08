-- =============================================================================
-- V49 : cloture journaliere des agences.
--
-- Pour une agence et une journee de caisse : solde theorique de chaque compte
-- (tire du registre), solde reel compte ou releve chez l'operateur, ecart
-- justifie. La cloture est soumise, puis validee par un superviseur ou
-- l'administrateur : la journee est alors verrouillee, plus aucune ecriture
-- n'y entre. Une correction ulterieure exige une reouverture motivee, tracee.
-- =============================================================================

CREATE TABLE agc_cloture (
    id                BIGSERIAL PRIMARY KEY,
    entreprise_id     BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id         BIGINT NOT NULL REFERENCES agc_agence (id),
    journee           DATE NOT NULL,
    statut            VARCHAR(20) NOT NULL,
    observations      VARCHAR(1000),
    soumise_le        TIMESTAMPTZ,
    soumise_par_id    BIGINT,
    soumise_par_nom   VARCHAR(150),
    validee_le        TIMESTAMPTZ,
    validee_par_id    BIGINT,
    validee_par_nom   VARCHAR(150),
    motif_rejet       VARCHAR(500),
    rouverte_le       TIMESTAMPTZ,
    rouverte_par_nom  VARCHAR(150),
    motif_reouverture VARCHAR(500),
    cree_le           TIMESTAMPTZ,
    cree_par          VARCHAR(100),
    modifie_le        TIMESTAMPTZ,
    modifie_par       VARCHAR(100),
    version           INTEGER,
    CONSTRAINT uk_agc_cloture UNIQUE (agence_id, journee),
    CONSTRAINT ck_agc_cloture_statut CHECK (statut IN ('SOUMISE', 'VALIDEE', 'REJETEE', 'ROUVERTE'))
);
CREATE INDEX ix_agc_cloture_agence ON agc_cloture (agence_id, journee DESC);

CREATE TABLE agc_cloture_ligne (
    id               BIGSERIAL PRIMARY KEY,
    cloture_id       BIGINT NOT NULL REFERENCES agc_cloture (id) ON DELETE CASCADE,
    compte_id        BIGINT NOT NULL REFERENCES agc_compte (id),
    libelle          VARCHAR(120) NOT NULL,
    famille          VARCHAR(20) NOT NULL,
    solde_theorique  BIGINT NOT NULL,
    -- Nul : compte non controle (seuls le cash et le virtuel sont obligatoires).
    solde_reel       BIGINT,
    ecart            BIGINT,
    justification    VARCHAR(500)
);
CREATE INDEX ix_agc_cloture_ligne ON agc_cloture_ligne (cloture_id);
