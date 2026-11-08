-- =============================================================================
-- V47 : transactions des agences (depot, retrait, envoi, paiement, recharge...).
--
-- Une transaction est validee des sa saisie : ses lignes s'ecrivent aussitot
-- au registre (cash, virtuel, commission). Elle ne se modifie plus ; une
-- annulation, motivee, passe par l'ecriture inverse. La regle de mouvement et
-- la commission sont recopiees dans la transaction : un changement ulterieur
-- du catalogue ou d'un bareme ne la touche pas.
-- =============================================================================

-- Fiches clients facultatives, retrouvees par leur numero de telephone.
CREATE TABLE agc_client (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    telephone      VARCHAR(30) NOT NULL,
    nom            VARCHAR(150),
    type_piece     VARCHAR(40),
    numero_piece   VARCHAR(60),
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT uk_agc_client_telephone UNIQUE (entreprise_id, telephone)
);

CREATE TABLE agc_transaction (
    id                     BIGSERIAL PRIMARY KEY,
    entreprise_id          BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id              BIGINT NOT NULL REFERENCES agc_agence (id),
    reference              VARCHAR(30) NOT NULL,
    date_operation         TIMESTAMPTZ NOT NULL,
    journee                DATE NOT NULL,
    service_id             BIGINT NOT NULL REFERENCES agc_service (id),
    reseau_id              BIGINT REFERENCES agc_reseau (id),
    type_operation_id      BIGINT NOT NULL REFERENCES agc_type_operation (id),
    montant                BIGINT NOT NULL,
    -- Regle de mouvement au moment de la saisie.
    effet_cash             INTEGER NOT NULL,
    effet_virtuel          INTEGER NOT NULL,
    compte_virtuel         VARCHAR(20) NOT NULL,
    -- Commission retenue, commission proposee par le bareme, et trace d'une modification.
    commission             BIGINT NOT NULL DEFAULT 0,
    commission_proposee    BIGINT NOT NULL DEFAULT 0,
    bareme_id              BIGINT REFERENCES agc_bareme (id),
    compte_commission      VARCHAR(20) NOT NULL DEFAULT 'COMMISSIONS',
    motif_commission       VARCHAR(255),
    reference_operateur    VARCHAR(80),
    client_id              BIGINT REFERENCES agc_client (id),
    client_nom             VARCHAR(150),
    client_telephone       VARCHAR(30),
    client_type_piece      VARCHAR(40),
    client_numero_piece    VARCHAR(60),
    -- Carte rechargee : seulement les 4 derniers chiffres.
    carte_masquee          VARCHAR(20),
    observations           VARCHAR(500),
    caissier_id            BIGINT NOT NULL,
    caissier_nom           VARCHAR(150) NOT NULL,
    statut                 VARCHAR(20) NOT NULL DEFAULT 'VALIDEE',
    doublon_confirme       BOOLEAN NOT NULL DEFAULT FALSE,
    ecriture_id            BIGINT REFERENCES agc_ecriture (id),
    annulation_ecriture_id BIGINT REFERENCES agc_ecriture (id),
    annulee_le             TIMESTAMPTZ,
    annulee_par_id         BIGINT,
    annulee_par_nom        VARCHAR(150),
    motif_annulation       VARCHAR(500),
    cree_le                TIMESTAMPTZ,
    cree_par               VARCHAR(100),
    modifie_le             TIMESTAMPTZ,
    modifie_par            VARCHAR(100),
    version                INTEGER,
    CONSTRAINT uk_agc_transaction_reference UNIQUE (entreprise_id, reference),
    CONSTRAINT ck_agc_transaction_montant CHECK (montant > 0),
    CONSTRAINT ck_agc_transaction_commission CHECK (commission >= 0 AND commission_proposee >= 0),
    CONSTRAINT ck_agc_transaction_statut CHECK (statut IN ('VALIDEE', 'ANNULEE')),
    CONSTRAINT ck_agc_transaction_compte CHECK (compte_virtuel IN ('FLOAT', 'RECHARGE', 'AUCUN')),
    CONSTRAINT ck_agc_transaction_compte_commission CHECK (compte_commission IN ('COMMISSIONS', 'FLOAT'))
);
CREATE INDEX ix_agc_transaction_agence_date ON agc_transaction (agence_id, date_operation DESC);
CREATE INDEX ix_agc_transaction_journee ON agc_transaction (agence_id, journee);
CREATE INDEX ix_agc_transaction_caissier ON agc_transaction (caissier_id, date_operation DESC);
-- Detection des doublons : meme service, type et montant dans les dernieres minutes.
CREATE INDEX ix_agc_transaction_doublon ON agc_transaction (service_id, type_operation_id, montant, date_operation DESC);

-- Nouveau droit : modifier la commission proposee, accorde par defaut au superviseur.
INSERT INTO agc_permission (entreprise_id, role, permission)
SELECT entreprise_id, 'SUPERVISEUR_AGENCE', 'COMMISSION_MODIFIER' FROM agc_reglage
ON CONFLICT DO NOTHING;
