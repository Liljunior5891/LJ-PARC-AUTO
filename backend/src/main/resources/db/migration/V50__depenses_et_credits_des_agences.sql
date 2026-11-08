-- =============================================================================
-- V50 : depenses, charges recurrentes, credits et dettes des agences.
--
-- Une depense debite un compte de l'agence (caisse, banque...) par le
-- registre ; c'est une charge. Une charge recurrente (loyer, internet...) ne
-- se paie qu'une fois par mois : la generation du mois ne cree jamais de doublon.
--
-- Credits et dettes : emprunt recu, apport de fonds, avance accordee, dette.
-- L'argent recu (emprunt, apport) n'est pas un revenu ; le remboursement du
-- capital n'est pas une charge, seuls les interets et frais financiers le sont.
-- =============================================================================

CREATE TABLE agc_categorie_depense (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    nom            VARCHAR(80) NOT NULL,
    ordre          INTEGER NOT NULL DEFAULT 0,
    actif          BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER
);
CREATE UNIQUE INDEX uk_agc_categorie_depense ON agc_categorie_depense (entreprise_id, lower(nom));

CREATE TABLE agc_charge_recurrente (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id      BIGINT NOT NULL REFERENCES agc_agence (id),
    categorie_id   BIGINT NOT NULL REFERENCES agc_categorie_depense (id),
    libelle        VARCHAR(120) NOT NULL,
    montant        BIGINT NOT NULL,
    jour_echeance  INTEGER NOT NULL DEFAULT 1,
    actif          BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT ck_agc_charge_montant CHECK (montant > 0),
    CONSTRAINT ck_agc_charge_jour CHECK (jour_echeance BETWEEN 1 AND 28)
);

CREATE TABLE agc_depense (
    id                     BIGSERIAL PRIMARY KEY,
    entreprise_id          BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id              BIGINT NOT NULL REFERENCES agc_agence (id),
    numero                 VARCHAR(40) NOT NULL,
    categorie_id           BIGINT NOT NULL REFERENCES agc_categorie_depense (id),
    libelle                VARCHAR(255) NOT NULL,
    montant                BIGINT NOT NULL,
    journee                DATE NOT NULL,
    date_depense           TIMESTAMPTZ NOT NULL,
    compte_id              BIGINT NOT NULL REFERENCES agc_compte (id),
    beneficiaire           VARCHAR(150),
    reference              VARCHAR(80),
    -- Charge recurrente reglee et mois concerne (AAAA-MM).
    recurrente_id          BIGINT REFERENCES agc_charge_recurrente (id),
    periode                VARCHAR(7),
    observations           VARCHAR(500),
    justificatif_chemin    VARCHAR(255),
    justificatif_nom       VARCHAR(255),
    justificatif_type      VARCHAR(100),
    statut                 VARCHAR(20) NOT NULL DEFAULT 'VALIDEE',
    auteur_id              BIGINT NOT NULL,
    auteur_nom             VARCHAR(150) NOT NULL,
    ecriture_id            BIGINT REFERENCES agc_ecriture (id),
    annulation_ecriture_id BIGINT REFERENCES agc_ecriture (id),
    annulee_le             TIMESTAMPTZ,
    annulee_par_nom        VARCHAR(150),
    motif_annulation       VARCHAR(500),
    cree_le                TIMESTAMPTZ,
    cree_par               VARCHAR(100),
    modifie_le             TIMESTAMPTZ,
    modifie_par            VARCHAR(100),
    version                INTEGER,
    CONSTRAINT uk_agc_depense_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_agc_depense_montant CHECK (montant > 0),
    CONSTRAINT ck_agc_depense_statut CHECK (statut IN ('VALIDEE', 'ANNULEE'))
);
CREATE INDEX ix_agc_depense_agence ON agc_depense (agence_id, journee DESC);
-- Une charge recurrente ne se paie qu'une fois par mois (hors depense annulee).
CREATE UNIQUE INDEX uk_agc_depense_recurrente ON agc_depense (recurrente_id, periode)
    WHERE recurrente_id IS NOT NULL AND statut = 'VALIDEE';

CREATE TABLE agc_credit (
    id                   BIGSERIAL PRIMARY KEY,
    entreprise_id        BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id            BIGINT NOT NULL REFERENCES agc_agence (id),
    numero               VARCHAR(40) NOT NULL,
    type                 VARCHAR(20) NOT NULL,
    libelle              VARCHAR(255) NOT NULL,
    tiers_nom            VARCHAR(150) NOT NULL,
    tiers_telephone      VARCHAR(30),
    montant_initial      BIGINT NOT NULL,
    -- Capital restant du ; zero : solde.
    capital_restant      BIGINT NOT NULL,
    date_debut           DATE NOT NULL,
    date_echeance        DATE,
    taux_interet         NUMERIC(7, 4),
    -- Compte qui a recu (ou verse) l'argent a l'ouverture ; nul pour une dette sans mouvement.
    compte_id            BIGINT REFERENCES agc_compte (id),
    remboursable         BOOLEAN NOT NULL DEFAULT TRUE,
    observations         VARCHAR(500),
    statut               VARCHAR(20) NOT NULL DEFAULT 'EN_COURS',
    ecriture_id          BIGINT REFERENCES agc_ecriture (id),
    auteur_nom           VARCHAR(150),
    cree_le              TIMESTAMPTZ,
    cree_par             VARCHAR(100),
    modifie_le           TIMESTAMPTZ,
    modifie_par          VARCHAR(100),
    version              INTEGER,
    CONSTRAINT uk_agc_credit_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_agc_credit_type CHECK (type IN ('EMPRUNT', 'APPORT', 'AVANCE', 'DETTE')),
    CONSTRAINT ck_agc_credit_statut CHECK (statut IN ('EN_COURS', 'SOLDE', 'ANNULE')),
    CONSTRAINT ck_agc_credit_montants CHECK (montant_initial > 0 AND capital_restant >= 0 AND capital_restant <= montant_initial)
);
CREATE INDEX ix_agc_credit_agence ON agc_credit (agence_id, statut);

-- Echeancier prevu (facultatif).
CREATE TABLE agc_credit_echeance (
    id         BIGSERIAL PRIMARY KEY,
    credit_id  BIGINT NOT NULL REFERENCES agc_credit (id) ON DELETE CASCADE,
    date_prevue DATE NOT NULL,
    montant    BIGINT NOT NULL,
    CONSTRAINT ck_agc_echeance_montant CHECK (montant > 0)
);

CREATE TABLE agc_remboursement (
    id                     BIGSERIAL PRIMARY KEY,
    entreprise_id          BIGINT NOT NULL REFERENCES entreprise (id),
    credit_id              BIGINT NOT NULL REFERENCES agc_credit (id),
    agence_id              BIGINT NOT NULL REFERENCES agc_agence (id),
    date_remboursement     TIMESTAMPTZ NOT NULL,
    journee                DATE NOT NULL,
    capital                BIGINT NOT NULL DEFAULT 0,
    -- Interets et frais financiers : seule partie qui est une charge.
    interets               BIGINT NOT NULL DEFAULT 0,
    compte_id              BIGINT NOT NULL REFERENCES agc_compte (id),
    observations           VARCHAR(500),
    statut                 VARCHAR(20) NOT NULL DEFAULT 'VALIDE',
    auteur_nom             VARCHAR(150),
    ecriture_id            BIGINT REFERENCES agc_ecriture (id),
    annulation_ecriture_id BIGINT REFERENCES agc_ecriture (id),
    motif_annulation       VARCHAR(500),
    cree_le                TIMESTAMPTZ,
    cree_par               VARCHAR(100),
    modifie_le             TIMESTAMPTZ,
    modifie_par            VARCHAR(100),
    version                INTEGER,
    CONSTRAINT ck_agc_remboursement_montants CHECK (capital >= 0 AND interets >= 0 AND capital + interets > 0),
    CONSTRAINT ck_agc_remboursement_statut CHECK (statut IN ('VALIDE', 'ANNULE'))
);
CREATE INDEX ix_agc_remboursement_credit ON agc_remboursement (credit_id);
CREATE INDEX ix_agc_remboursement_journee ON agc_remboursement (agence_id, journee);

-- Nouveau droit : annuler une depense, accorde par defaut au superviseur.
INSERT INTO agc_permission (entreprise_id, role, permission)
SELECT entreprise_id, 'SUPERVISEUR_AGENCE', 'DEPENSE_ANNULER' FROM agc_reglage
ON CONFLICT DO NOTHING;
