-- Validation des depenses payees par la caisse : reglage par module. Active, une
-- depense (ou une sortie d'especes au point de vente) payee par la caisse attend
-- la validation du gerant (point de vente) ou du superviseur (agences), puis celle
-- de l'administrateur. Tant qu'elle attend, rien ne sort de la caisse et les
-- journaux ne bougent pas ; la sortie est passee a la validation de l'administrateur.

ALTER TABLE pdv_reglage ADD COLUMN validation_depenses_caisse BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE agc_reglage ADD COLUMN validation_depenses_caisse BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------- point de vente

ALTER TABLE pdv_depense DROP CONSTRAINT ck_pdv_depense_statut;
ALTER TABLE pdv_depense ADD CONSTRAINT ck_pdv_depense_statut
    CHECK (statut IN ('ATTENTE_GERANT', 'ATTENTE_ADMIN', 'VALIDE', 'REFUSEE', 'ANNULEE'));
ALTER TABLE pdv_depense ADD COLUMN visee_par     VARCHAR(150);
ALTER TABLE pdv_depense ADD COLUMN visee_le      TIMESTAMPTZ;
ALTER TABLE pdv_depense ADD COLUMN approuvee_par VARCHAR(150);
ALTER TABLE pdv_depense ADD COLUMN approuvee_le  TIMESTAMPTZ;
ALTER TABLE pdv_depense ADD COLUMN refusee_par   VARCHAR(150);
ALTER TABLE pdv_depense ADD COLUMN refusee_le    TIMESTAMPTZ;
ALTER TABLE pdv_depense ADD COLUMN motif_refus   VARCHAR(255);
CREATE INDEX idx_pdv_depense_attente ON pdv_depense (statut) WHERE statut IN ('ATTENTE_GERANT', 'ATTENTE_ADMIN');

-- Sortie d'especes demandee a la caisse, en attente des validations.
CREATE TABLE pdv_sortie_attente (
    id                  BIGSERIAL PRIMARY KEY,
    entreprise_id       BIGINT NOT NULL REFERENCES entreprise (id),
    numero              VARCHAR(40) NOT NULL,
    session_id          BIGINT NOT NULL REFERENCES pdv_session_caisse (id),
    montant             NUMERIC(16, 2) NOT NULL,
    motif               VARCHAR(255) NOT NULL,
    auteur_id           BIGINT NOT NULL,
    auteur_nom          VARCHAR(150) NOT NULL,
    demandee_le         TIMESTAMPTZ NOT NULL,
    statut              VARCHAR(20) NOT NULL,
    visee_par           VARCHAR(150),
    visee_le            TIMESTAMPTZ,
    approuvee_par       VARCHAR(150),
    approuvee_le        TIMESTAMPTZ,
    refusee_par         VARCHAR(150),
    refusee_le          TIMESTAMPTZ,
    motif_refus         VARCHAR(255),
    mouvement_caisse_id BIGINT REFERENCES pdv_mouvement_caisse (id) ON DELETE SET NULL,
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT uk_pdv_sortie_attente_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_sortie_attente_montant CHECK (montant > 0),
    CONSTRAINT ck_pdv_sortie_attente_statut CHECK (statut IN ('ATTENTE_GERANT', 'ATTENTE_ADMIN', 'VALIDEE', 'REFUSEE'))
);
CREATE INDEX idx_pdv_sortie_attente_statut ON pdv_sortie_attente (statut, demandee_le DESC);
CREATE INDEX idx_pdv_sortie_attente_session ON pdv_sortie_attente (session_id);

-- ---------------------------------------------------------------------- agences

ALTER TABLE agc_depense DROP CONSTRAINT ck_agc_depense_statut;
ALTER TABLE agc_depense ADD CONSTRAINT ck_agc_depense_statut
    CHECK (statut IN ('ATTENTE_SUPERVISEUR', 'ATTENTE_ADMIN', 'VALIDEE', 'REFUSEE', 'ANNULEE'));
ALTER TABLE agc_depense ADD COLUMN visee_par_nom     VARCHAR(150);
ALTER TABLE agc_depense ADD COLUMN visee_le          TIMESTAMPTZ;
ALTER TABLE agc_depense ADD COLUMN approuvee_par_nom VARCHAR(150);
ALTER TABLE agc_depense ADD COLUMN approuvee_le      TIMESTAMPTZ;
ALTER TABLE agc_depense ADD COLUMN refusee_par_nom   VARCHAR(150);
ALTER TABLE agc_depense ADD COLUMN refusee_le        TIMESTAMPTZ;
ALTER TABLE agc_depense ADD COLUMN motif_refus       VARCHAR(500);
-- Une charge recurrente ne se paie qu'une fois par mois : une demande en attente compte deja.
DROP INDEX uk_agc_depense_recurrente;
CREATE UNIQUE INDEX uk_agc_depense_recurrente ON agc_depense (recurrente_id, periode)
    WHERE recurrente_id IS NOT NULL AND statut IN ('VALIDEE', 'ATTENTE_SUPERVISEUR', 'ATTENTE_ADMIN');
CREATE INDEX ix_agc_depense_attente ON agc_depense (agence_id, statut) WHERE statut IN ('ATTENTE_SUPERVISEUR', 'ATTENTE_ADMIN');

-- Nouveau droit : viser une depense payee par la caisse, accorde par defaut au superviseur.
INSERT INTO agc_permission (entreprise_id, role, permission)
SELECT entreprise_id, 'SUPERVISEUR_AGENCE', 'DEPENSE_VALIDER' FROM agc_reglage
ON CONFLICT DO NOTHING;
