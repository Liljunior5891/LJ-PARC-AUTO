-- Operations des caissiers d'agence saisies sans connexion (transactions, ventes de cartes,
-- depenses), envoyees au retour du reseau. La cle evite un double enregistrement ; une
-- operation que le serveur refuse devient un conflit a traiter par le superviseur.
CREATE TABLE agc_operation_hors_ligne (
    id               BIGSERIAL PRIMARY KEY,
    entreprise_id    BIGINT       NOT NULL REFERENCES entreprise (id),
    cle              VARCHAR(64)  NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    agence_id        BIGINT       REFERENCES agc_agence (id),
    auteur_id        BIGINT       NOT NULL,
    auteur_nom       VARCHAR(150) NOT NULL,
    saisie_le        TIMESTAMPTZ  NOT NULL,
    recue_le         TIMESTAMPTZ  NOT NULL,
    -- ENREGISTREE, CONFLIT, REPRISE (reussie apres conflit), ABANDONNEE
    statut           VARCHAR(20)  NOT NULL,
    resume           VARCHAR(255) NOT NULL,
    donnees          TEXT         NOT NULL,
    reference        VARCHAR(40),
    motif            VARCHAR(1000),
    traitee_par_nom  VARCHAR(150),
    traitee_le       TIMESTAMPTZ,
    note_traitement  VARCHAR(500),
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT uk_agc_operation_hors_ligne UNIQUE (entreprise_id, cle),
    CONSTRAINT ck_agc_operation_hors_ligne_type CHECK (type IN ('TRANSACTION', 'VENTE_CARTE', 'DEPENSE')),
    CONSTRAINT ck_agc_operation_hors_ligne_statut CHECK (statut IN ('ENREGISTREE', 'CONFLIT', 'REPRISE', 'ABANDONNEE'))
);
CREATE INDEX ix_agc_operation_hors_ligne_statut ON agc_operation_hors_ligne (entreprise_id, statut, saisie_le DESC);
