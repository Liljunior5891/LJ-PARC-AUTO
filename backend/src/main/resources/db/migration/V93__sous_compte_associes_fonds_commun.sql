-- =============================================================================
-- V93 : sous-compte des associes et fonds de reserve commun.
--
-- La part de chaque associe (marge brute de ses produits) est figee a la fin de
-- chaque mois : c'est ce que l'entreprise lui doit. Le gerant lui verse cette part
-- par un depot en banque (bordereau joint), depuis son coffre ou le compte de
-- l'entreprise ; l'associe valide le depot, qui alimente alors son sous-compte,
-- ou le refuse avec un motif (rien ne bouge, le gerant corrige).
--
-- Le fonds de reserve n'est plus alimente par un pourcentage du benefice : il recoit
-- les apports des associes (depuis leur sous-compte) et les apports directs de
-- l'administrateur (depuis le compte de l'entreprise). Aucun mois n'avait encore
-- ete verse au fonds : l'ancien calcul est retire sans reprise.
-- =============================================================================

DROP TABLE pdv_fonds_reserve_mois;
DROP TABLE pdv_fonds_reserve_periode;
ALTER TABLE pdv_reglage DROP CONSTRAINT ck_pdv_reglage_fonds_reserve;
ALTER TABLE pdv_reglage DROP COLUMN fonds_reserve_pourcent;

-- Mois dont la part des associes est figee (un enregistrement par mois clos).
CREATE TABLE pdv_associe_cloture (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    mois          DATE   NOT NULL,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_associe_cloture UNIQUE (entreprise_id, mois),
    CONSTRAINT ck_pdv_associe_cloture_mois CHECK (EXTRACT(DAY FROM mois) = 1)
);

-- Part figee d'un associe pour un mois clos (absente si elle est nulle).
CREATE TABLE pdv_associe_mois (
    id               BIGSERIAL PRIMARY KEY,
    entreprise_id    BIGINT         NOT NULL REFERENCES entreprise (id),
    associe_id       BIGINT         NOT NULL REFERENCES utilisateur (id),
    mois             DATE           NOT NULL,
    chiffre_affaires NUMERIC(16, 2) NOT NULL,
    marge            NUMERIC(16, 2) NOT NULL,
    part             NUMERIC(16, 2) NOT NULL,
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT uk_pdv_associe_mois UNIQUE (entreprise_id, associe_id, mois)
);

-- Versement de sa part a un associe : depot en banque saisi par le gerant.
CREATE TABLE pdv_associe_depot (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT         NOT NULL REFERENCES entreprise (id),
    numero         VARCHAR(40)    NOT NULL,
    associe_id     BIGINT         NOT NULL REFERENCES utilisateur (id),
    associe_nom    VARCHAR(150)   NOT NULL,
    montant        NUMERIC(16, 2) NOT NULL,
    -- COFFRE : especes du coffre du gerant ; BANQUE : compte de l'entreprise.
    source         VARCHAR(20)    NOT NULL,
    coffre_id      BIGINT,
    banque         VARCHAR(100),
    reference      VARCHAR(80)    NOT NULL,
    date_depot     DATE           NOT NULL,
    -- Periode couverte, librement : "octobre 2026", "exercice 2026"...
    periode        VARCHAR(100),
    note           VARCHAR(2000),
    piece_chemin   VARCHAR(255),
    piece_nom      VARCHAR(255),
    piece_type     VARCHAR(100),
    statut         VARCHAR(20)    NOT NULL DEFAULT 'EN_ATTENTE',
    motif_refus    VARCHAR(2000),
    auteur_id      BIGINT         NOT NULL,
    auteur_nom     VARCHAR(150)   NOT NULL,
    soumis_le      TIMESTAMPTZ    NOT NULL,
    traite_le      TIMESTAMPTZ,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT uk_pdv_associe_depot_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_associe_depot_montant CHECK (montant > 0),
    CONSTRAINT ck_pdv_associe_depot_source CHECK (source IN ('COFFRE', 'BANQUE')),
    CONSTRAINT ck_pdv_associe_depot_coffre CHECK ((source = 'COFFRE') = (coffre_id IS NOT NULL)),
    CONSTRAINT ck_pdv_associe_depot_statut CHECK (statut IN ('EN_ATTENTE', 'VALIDE', 'REFUSE', 'ANNULE'))
);
CREATE INDEX idx_pdv_associe_depot ON pdv_associe_depot (entreprise_id, associe_id, statut);

-- Apport au fonds de reserve : d'un associe (depuis son sous-compte) ou de
-- l'entreprise (apport direct de l'administrateur, depuis le compte de l'entreprise).
CREATE TABLE pdv_fonds_apport (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT         NOT NULL REFERENCES entreprise (id),
    numero        VARCHAR(40)    NOT NULL,
    origine       VARCHAR(20)    NOT NULL,
    associe_id    BIGINT REFERENCES utilisateur (id),
    associe_nom   VARCHAR(150),
    montant       NUMERIC(16, 2) NOT NULL,
    date_apport   TIMESTAMPTZ    NOT NULL,
    note          VARCHAR(500),
    auteur_id     BIGINT         NOT NULL,
    auteur_nom    VARCHAR(150)   NOT NULL,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_fonds_apport_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_fonds_apport_montant CHECK (montant > 0),
    CONSTRAINT ck_pdv_fonds_apport_origine CHECK (origine IN ('ASSOCIE', 'ENTREPRISE')),
    CONSTRAINT ck_pdv_fonds_apport_associe CHECK ((origine = 'ASSOCIE') = (associe_id IS NOT NULL))
);
CREATE INDEX idx_pdv_fonds_apport ON pdv_fonds_apport (entreprise_id, associe_id);
