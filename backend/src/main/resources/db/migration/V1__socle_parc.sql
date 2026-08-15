-- =====================================================================
-- PARC AUTO - V1 : socle de securite et priorite 1 (parc)
-- Chapitres 6, 8, 9, 21 et 40 du cahier des charges.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Utilisateurs et roles
-- ---------------------------------------------------------------------
CREATE TABLE utilisateur (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(150) NOT NULL UNIQUE,
    mot_de_passe  VARCHAR(100) NOT NULL,
    nom           VARCHAR(100) NOT NULL,
    prenom        VARCHAR(100),
    telephone     VARCHAR(30),
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    chauffeur_id  BIGINT,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER
);

CREATE TABLE utilisateur_role (
    utilisateur_id BIGINT NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    role           VARCHAR(40) NOT NULL,
    PRIMARY KEY (utilisateur_id, role)
);

-- ---------------------------------------------------------------------
-- Camions (chapitre 6)
-- ---------------------------------------------------------------------
CREATE TABLE camion (
    id                  BIGSERIAL PRIMARY KEY,
    numero_interne      VARCHAR(40) NOT NULL UNIQUE,
    immatriculation     VARCHAR(40) NOT NULL UNIQUE,
    marque              VARCHAR(60),
    modele              VARCHAR(60),
    annee               INTEGER,
    type                VARCHAR(60),
    categorie           VARCHAR(60),
    numero_chassis      VARCHAR(60),
    numero_moteur       VARCHAR(60),
    couleur             VARCHAR(40),
    capacite_tonnes     NUMERIC(10, 2),
    type_carburant      VARCHAR(20),
    date_acquisition    DATE,
    prix_achat          NUMERIC(16, 2),
    kilometrage_initial BIGINT NOT NULL DEFAULT 0,
    kilometrage_actuel  BIGINT NOT NULL DEFAULT 0,
    statut              VARCHAR(30) NOT NULL DEFAULT 'DISPONIBLE',
    photo_url           VARCHAR(500),
    gps_device_id       VARCHAR(80),
    observations        TEXT,
    actif               BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT camion_km_coherent CHECK (kilometrage_actuel >= kilometrage_initial)
);

CREATE INDEX idx_camion_statut ON camion (statut);
CREATE INDEX idx_camion_actif ON camion (actif);

-- ---------------------------------------------------------------------
-- Chauffeurs (chapitre 8)
-- ---------------------------------------------------------------------
CREATE TABLE chauffeur (
    id                 BIGSERIAL PRIMARY KEY,
    matricule          VARCHAR(40) NOT NULL UNIQUE,
    nom                VARCHAR(80) NOT NULL,
    prenom             VARCHAR(80) NOT NULL,
    photo_url          VARCHAR(500),
    telephone          VARCHAR(30) NOT NULL,
    telephone_urgence  VARCHAR(30),
    email              VARCHAR(150),
    adresse            VARCHAR(255),
    date_naissance     DATE,
    numero_permis      VARCHAR(60),
    categories_permis  VARCHAR(100),
    permis_obtention   DATE,
    permis_expiration  DATE,
    date_embauche      DATE,
    salaire_mensuel    NUMERIC(16, 2),
    statut             VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
    observations       TEXT,
    actif              BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le            TIMESTAMPTZ,
    cree_par           VARCHAR(100),
    modifie_le         TIMESTAMPTZ,
    modifie_par        VARCHAR(100),
    version            INTEGER
);

CREATE INDEX idx_chauffeur_statut ON chauffeur (statut);
CREATE INDEX idx_chauffeur_permis_expiration ON chauffeur (permis_expiration);

ALTER TABLE utilisateur
    ADD CONSTRAINT fk_utilisateur_chauffeur FOREIGN KEY (chauffeur_id) REFERENCES chauffeur (id);

-- ---------------------------------------------------------------------
-- Affectations chauffeur / camion (chapitre 9)
-- ---------------------------------------------------------------------
CREATE TABLE affectation (
    id           BIGSERIAL PRIMARY KEY,
    camion_id    BIGINT NOT NULL REFERENCES camion (id),
    chauffeur_id BIGINT NOT NULL REFERENCES chauffeur (id),
    date_debut   DATE NOT NULL,
    date_fin     DATE,
    motif        VARCHAR(255),
    observations TEXT,
    cree_le      TIMESTAMPTZ,
    cree_par     VARCHAR(100),
    modifie_le   TIMESTAMPTZ,
    modifie_par  VARCHAR(100),
    version      INTEGER,
    CONSTRAINT affectation_periode_coherente CHECK (date_fin IS NULL OR date_fin >= date_debut)
);

CREATE INDEX idx_affectation_camion ON affectation (camion_id);
CREATE INDEX idx_affectation_chauffeur ON affectation (chauffeur_id);

-- Un camion et un chauffeur ne peuvent avoir qu'une seule affectation ouverte.
CREATE UNIQUE INDEX uk_affectation_camion_ouverte
    ON affectation (camion_id) WHERE date_fin IS NULL;
CREATE UNIQUE INDEX uk_affectation_chauffeur_ouverte
    ON affectation (chauffeur_id) WHERE date_fin IS NULL;

-- ---------------------------------------------------------------------
-- Documents (chapitre 21)
-- ---------------------------------------------------------------------
CREATE TABLE document (
    id                BIGSERIAL PRIMARY KEY,
    proprietaire_type VARCHAR(20) NOT NULL,
    proprietaire_id   BIGINT NOT NULL,
    type              VARCHAR(30) NOT NULL,
    libelle           VARCHAR(150) NOT NULL,
    numero            VARCHAR(80),
    emetteur          VARCHAR(150),
    date_emission     DATE,
    date_expiration   DATE,
    cout              NUMERIC(16, 2),
    fichier_nom       VARCHAR(255),
    fichier_chemin    VARCHAR(500),
    fichier_type      VARCHAR(120),
    fichier_taille    BIGINT,
    observations      TEXT,
    archive           BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le           TIMESTAMPTZ,
    cree_par          VARCHAR(100),
    modifie_le        TIMESTAMPTZ,
    modifie_par       VARCHAR(100),
    version           INTEGER
);

CREATE INDEX idx_document_proprietaire ON document (proprietaire_type, proprietaire_id);
CREATE INDEX idx_document_expiration ON document (date_expiration);
