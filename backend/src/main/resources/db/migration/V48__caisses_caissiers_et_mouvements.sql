-- =============================================================================
-- V48 : caisses des caissiers et mouvements entre comptes des agences.
--
-- Caisse de caissier : le superviseur l'ouvre en comptant le fond (billettage),
-- pris sur la caisse generale ; le caissier y encaisse ses transactions ; a la
-- fermeture, le superviseur compte le tiroir, l'ecart est justifie et regularise,
-- puis l'argent compte revient a la caisse generale. Chaque caisse ne voit que
-- ses operations.
--
-- Mouvement : transfert entre deux comptes de l'agence (approvisionnement d'un
-- float, versement ou retrait bancaire...) ou regularisation d'un compte. Il ne
-- cree ni recette ni depense. Au-dela du seuil regle, et toujours pour une
-- regularisation, il attend la validation d'une autre personne habilitee.
-- =============================================================================

CREATE TABLE agc_session_caisse (
    id                     BIGSERIAL PRIMARY KEY,
    entreprise_id          BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id              BIGINT NOT NULL REFERENCES agc_agence (id),
    compte_id              BIGINT NOT NULL REFERENCES agc_compte (id),
    caissier_id            BIGINT NOT NULL REFERENCES utilisateur (id),
    caissier_nom           VARCHAR(150) NOT NULL,
    numero                 VARCHAR(40) NOT NULL,
    statut                 VARCHAR(20) NOT NULL DEFAULT 'OUVERTE',
    ouverte_le             TIMESTAMPTZ NOT NULL,
    ouverte_par_id         BIGINT,
    ouverte_par_nom        VARCHAR(150),
    fond_initial           BIGINT NOT NULL DEFAULT 0,
    billettage_ouverture   TEXT,
    ecriture_ouverture_id  BIGINT REFERENCES agc_ecriture (id),
    fermee_le              TIMESTAMPTZ,
    fermee_par_id          BIGINT,
    fermee_par_nom         VARCHAR(150),
    montant_theorique      BIGINT,
    montant_compte         BIGINT,
    ecart                  BIGINT,
    justification          VARCHAR(1000),
    billettage_fermeture   TEXT,
    ecriture_fermeture_id  BIGINT REFERENCES agc_ecriture (id),
    cree_le                TIMESTAMPTZ,
    cree_par               VARCHAR(100),
    modifie_le             TIMESTAMPTZ,
    modifie_par            VARCHAR(100),
    version                INTEGER,
    CONSTRAINT uk_agc_session_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_agc_session_statut CHECK (statut IN ('OUVERTE', 'FERMEE'))
);
-- Un caissier n'a qu'une caisse ouverte a la fois.
CREATE UNIQUE INDEX uk_agc_session_ouverte ON agc_session_caisse (caissier_id) WHERE statut = 'OUVERTE';
CREATE INDEX ix_agc_session_agence ON agc_session_caisse (agence_id, ouverte_le DESC);

CREATE TABLE agc_mouvement (
    id                     BIGSERIAL PRIMARY KEY,
    entreprise_id          BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id              BIGINT NOT NULL REFERENCES agc_agence (id),
    reference              VARCHAR(40) NOT NULL,
    type                   VARCHAR(30) NOT NULL,
    -- Transfert : source et destination ; regularisation : un seul des deux.
    compte_source_id       BIGINT REFERENCES agc_compte (id),
    compte_destination_id  BIGINT REFERENCES agc_compte (id),
    montant                BIGINT NOT NULL,
    motif                  VARCHAR(255) NOT NULL,
    justification          VARCHAR(1000),
    piece_chemin           VARCHAR(255),
    piece_nom              VARCHAR(255),
    piece_type             VARCHAR(100),
    statut                 VARCHAR(20) NOT NULL,
    date_mouvement         TIMESTAMPTZ NOT NULL,
    auteur_id              BIGINT NOT NULL,
    auteur_nom             VARCHAR(150) NOT NULL,
    valide_le              TIMESTAMPTZ,
    valide_par_id          BIGINT,
    valide_par_nom         VARCHAR(150),
    motif_rejet            VARCHAR(500),
    ecriture_id            BIGINT REFERENCES agc_ecriture (id),
    annulation_ecriture_id BIGINT REFERENCES agc_ecriture (id),
    motif_annulation       VARCHAR(500),
    cree_le                TIMESTAMPTZ,
    cree_par               VARCHAR(100),
    modifie_le             TIMESTAMPTZ,
    modifie_par            VARCHAR(100),
    version                INTEGER,
    CONSTRAINT uk_agc_mouvement_reference UNIQUE (entreprise_id, reference),
    CONSTRAINT ck_agc_mouvement_montant CHECK (montant > 0),
    CONSTRAINT ck_agc_mouvement_statut CHECK (statut IN ('EN_ATTENTE', 'VALIDE', 'REJETE', 'ANNULE')),
    CONSTRAINT ck_agc_mouvement_type CHECK (type IN ('APPROVISIONNEMENT', 'TRANSFERT_INTERNE', 'VERSEMENT_BANCAIRE',
                                                     'RETRAIT_BANCAIRE', 'CREDIT_COMMISSION', 'REGULARISATION', 'AUTRE')),
    CONSTRAINT ck_agc_mouvement_comptes CHECK (compte_source_id IS NOT NULL OR compte_destination_id IS NOT NULL)
);
CREATE INDEX ix_agc_mouvement_agence ON agc_mouvement (agence_id, date_mouvement DESC);

-- Les transactions retiennent la caisse ou est passe le cash, et la session du caissier.
ALTER TABLE agc_transaction ADD COLUMN compte_cash_id BIGINT REFERENCES agc_compte (id);
ALTER TABLE agc_transaction ADD COLUMN session_id BIGINT REFERENCES agc_session_caisse (id);
CREATE INDEX ix_agc_transaction_session ON agc_transaction (session_id);

-- Nouveau droit : ouvrir et fermer les caisses des caissiers, accorde par defaut au superviseur.
INSERT INTO agc_permission (entreprise_id, role, permission)
SELECT entreprise_id, 'SUPERVISEUR_AGENCE', 'CAISSE_GERER' FROM agc_reglage
ON CONFLICT DO NOTHING;
