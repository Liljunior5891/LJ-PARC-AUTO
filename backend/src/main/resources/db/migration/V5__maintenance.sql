-- =====================================================================
-- PARC AUTO - V5 : priorite 5 (maintenance)
-- Chapitres 19, 20 et 33 du cahier des charges.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Entretiens et reparations (chapitre 19)
-- La periodicite est exprimee en kilometres, en jours, ou les deux :
-- la premiere echeance atteinte declenche l'alerte.
-- ---------------------------------------------------------------------
CREATE TABLE entretien (
    id                    BIGSERIAL PRIMARY KEY,
    numero                VARCHAR(40) NOT NULL UNIQUE,
    camion_id             BIGINT NOT NULL REFERENCES camion (id),
    depense_id            BIGINT REFERENCES depense (id),
    type                  VARCHAR(30) NOT NULL,
    statut                VARCHAR(20) NOT NULL DEFAULT 'PLANIFIE',
    libelle               VARCHAR(255) NOT NULL,
    date_prevue           DATE,
    date_realisation      DATE,
    kilometrage_prevu     BIGINT,
    kilometrage_realise   BIGINT,
    periodicite_km        BIGINT,
    periodicite_jours     INTEGER,
    cout                  NUMERIC(16, 2),
    fournisseur           VARCHAR(180),
    intervenant           VARCHAR(180),
    pieces_remplacees     TEXT,
    immobilisation_jours  INTEGER,
    observations          TEXT,
    cree_le               TIMESTAMPTZ,
    cree_par              VARCHAR(100),
    modifie_le            TIMESTAMPTZ,
    modifie_par           VARCHAR(100),
    version               INTEGER,
    CONSTRAINT entretien_cout_positif CHECK (cout IS NULL OR cout >= 0),
    CONSTRAINT entretien_periodicite_positive
        CHECK ((periodicite_km IS NULL OR periodicite_km > 0)
               AND (periodicite_jours IS NULL OR periodicite_jours > 0))
);

CREATE INDEX idx_entretien_camion ON entretien (camion_id);
CREATE INDEX idx_entretien_statut ON entretien (statut);
CREATE INDEX idx_entretien_echeance ON entretien (date_prevue);

-- ---------------------------------------------------------------------
-- Pneumatiques (chapitre 20)
-- ---------------------------------------------------------------------
CREATE TABLE pneu (
    id                   BIGSERIAL PRIMARY KEY,
    camion_id            BIGINT REFERENCES camion (id),
    depense_id           BIGINT REFERENCES depense (id),
    reference            VARCHAR(60) NOT NULL UNIQUE,
    marque               VARCHAR(80),
    dimension            VARCHAR(40),
    numero_serie         VARCHAR(80),
    position             VARCHAR(30),
    statut               VARCHAR(20) NOT NULL DEFAULT 'EN_STOCK',
    etat                 VARCHAR(20) NOT NULL DEFAULT 'NEUF',
    date_achat           DATE,
    prix_achat           NUMERIC(16, 2),
    date_montage         DATE,
    kilometrage_montage  BIGINT,
    date_depose          DATE,
    kilometrage_depose   BIGINT,
    duree_vie_prevue_km  BIGINT,
    motif_depose         VARCHAR(255),
    observations         TEXT,
    cree_le              TIMESTAMPTZ,
    cree_par             VARCHAR(100),
    modifie_le           TIMESTAMPTZ,
    modifie_par          VARCHAR(100),
    version              INTEGER,
    CONSTRAINT pneu_prix_positif CHECK (prix_achat IS NULL OR prix_achat >= 0),
    CONSTRAINT pneu_kilometrage_coherent
        CHECK (kilometrage_depose IS NULL OR kilometrage_montage IS NULL
               OR kilometrage_depose >= kilometrage_montage)
);

CREATE INDEX idx_pneu_camion ON pneu (camion_id);
CREATE INDEX idx_pneu_statut ON pneu (statut);

-- Une position ne peut porter qu'un seul pneu monte a la fois.
CREATE UNIQUE INDEX uk_pneu_position_montee
    ON pneu (camion_id, position) WHERE statut = 'MONTE';

-- ---------------------------------------------------------------------
-- Incidents declares par les chauffeurs (chapitre 33)
-- ---------------------------------------------------------------------
CREATE TABLE incident (
    id             BIGSERIAL PRIMARY KEY,
    numero         VARCHAR(40) NOT NULL UNIQUE,
    camion_id      BIGINT NOT NULL REFERENCES camion (id),
    chauffeur_id   BIGINT REFERENCES chauffeur (id),
    mission_id     BIGINT REFERENCES mission (id),
    entretien_id   BIGINT REFERENCES entretien (id),
    type           VARCHAR(30) NOT NULL,
    gravite        VARCHAR(20) NOT NULL DEFAULT 'MOYENNE',
    statut         VARCHAR(20) NOT NULL DEFAULT 'DECLARE',
    description    TEXT NOT NULL,
    lieu           VARCHAR(255),
    latitude       NUMERIC(10, 7),
    longitude      NUMERIC(10, 7),
    photo_url      VARCHAR(500),
    survenu_le     TIMESTAMPTZ NOT NULL,
    resolu_le      TIMESTAMPTZ,
    resolution     TEXT,
    cout_estime    NUMERIC(16, 2),
    immobilisant   BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER
);

CREATE INDEX idx_incident_camion ON incident (camion_id, survenu_le DESC);
CREATE INDEX idx_incident_statut ON incident (statut);
CREATE INDEX idx_incident_chauffeur ON incident (chauffeur_id);
