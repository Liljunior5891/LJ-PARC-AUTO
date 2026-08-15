-- =====================================================================
-- PARC AUTO - V3 : priorite 3 (finances)
-- Chapitres 15 a 18, 27, 28 et 29 a 31 du cahier des charges.
--
-- Regle structurante du chapitre 2 : toute recette et toute depense est
-- rattachee a un camion. C'est cette contrainte qui rend calculable la
-- rentabilite de chaque vehicule.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Factures (chapitre 27)
-- ---------------------------------------------------------------------
CREATE TABLE facture (
    id              BIGSERIAL PRIMARY KEY,
    numero          VARCHAR(40) NOT NULL UNIQUE,
    client_id       BIGINT NOT NULL REFERENCES client (id),
    location_id     BIGINT REFERENCES location (id),
    mission_id      BIGINT REFERENCES mission (id),
    statut          VARCHAR(25) NOT NULL DEFAULT 'BROUILLON',
    date_emission   DATE NOT NULL,
    date_echeance   DATE,
    periode_debut   DATE,
    periode_fin     DATE,
    montant_ht      NUMERIC(16, 2) NOT NULL DEFAULT 0,
    taux_tva        NUMERIC(5, 2) NOT NULL DEFAULT 18,
    montant_tva     NUMERIC(16, 2) NOT NULL DEFAULT 0,
    montant_ttc     NUMERIC(16, 2) NOT NULL DEFAULT 0,
    observations    TEXT,
    cree_le         TIMESTAMPTZ,
    cree_par        VARCHAR(100),
    modifie_le      TIMESTAMPTZ,
    modifie_par     VARCHAR(100),
    version         INTEGER,
    CONSTRAINT facture_montants_positifs CHECK (montant_ht >= 0 AND montant_ttc >= 0),
    CONSTRAINT facture_periode_coherente CHECK (periode_fin IS NULL OR periode_debut IS NULL
                                                OR periode_fin >= periode_debut)
);

CREATE INDEX idx_facture_client ON facture (client_id);
CREATE INDEX idx_facture_statut ON facture (statut);
CREATE INDEX idx_facture_echeance ON facture (date_echeance);

CREATE TABLE facture_ligne (
    id             BIGSERIAL PRIMARY KEY,
    facture_id     BIGINT NOT NULL REFERENCES facture (id) ON DELETE CASCADE,
    camion_id      BIGINT REFERENCES camion (id),
    designation    VARCHAR(255) NOT NULL,
    quantite       NUMERIC(14, 2) NOT NULL DEFAULT 1,
    unite          VARCHAR(30),
    prix_unitaire  NUMERIC(16, 2) NOT NULL DEFAULT 0,
    montant        NUMERIC(16, 2) NOT NULL DEFAULT 0,
    ordre          INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_facture_ligne_facture ON facture_ligne (facture_id);

-- ---------------------------------------------------------------------
-- Paiements (chapitre 28)
-- ---------------------------------------------------------------------
CREATE TABLE paiement (
    id             BIGSERIAL PRIMARY KEY,
    facture_id     BIGINT NOT NULL REFERENCES facture (id),
    montant        NUMERIC(16, 2) NOT NULL,
    date_paiement  DATE NOT NULL,
    mode_paiement  VARCHAR(25) NOT NULL,
    reference      VARCHAR(80),
    observations   TEXT,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT paiement_montant_positif CHECK (montant > 0)
);

CREATE INDEX idx_paiement_facture ON paiement (facture_id);
CREATE INDEX idx_paiement_date ON paiement (date_paiement);

-- ---------------------------------------------------------------------
-- Recettes (chapitre 15)
-- ---------------------------------------------------------------------
CREATE TABLE recette (
    id             BIGSERIAL PRIMARY KEY,
    numero         VARCHAR(40) NOT NULL UNIQUE,
    camion_id      BIGINT NOT NULL REFERENCES camion (id),
    client_id      BIGINT REFERENCES client (id),
    location_id    BIGINT REFERENCES location (id),
    mission_id     BIGINT REFERENCES mission (id),
    facture_id     BIGINT REFERENCES facture (id),
    type           VARCHAR(30) NOT NULL,
    libelle        VARCHAR(255) NOT NULL,
    montant        NUMERIC(16, 2) NOT NULL,
    date_recette   DATE NOT NULL,
    mode_paiement  VARCHAR(25),
    reference      VARCHAR(80),
    justificatif   VARCHAR(500),
    observations   TEXT,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT recette_montant_positif CHECK (montant >= 0)
);

CREATE INDEX idx_recette_camion ON recette (camion_id);
CREATE INDEX idx_recette_date ON recette (date_recette);
CREATE INDEX idx_recette_type ON recette (type);
CREATE INDEX idx_recette_client ON recette (client_id);

-- ---------------------------------------------------------------------
-- Depenses (chapitres 16 et 18)
-- Les colonnes de carburant ne sont renseignees que pour la categorie CARBURANT.
-- ---------------------------------------------------------------------
CREATE TABLE depense (
    id              BIGSERIAL PRIMARY KEY,
    numero          VARCHAR(40) NOT NULL UNIQUE,
    camion_id       BIGINT NOT NULL REFERENCES camion (id),
    chauffeur_id    BIGINT REFERENCES chauffeur (id),
    mission_id      BIGINT REFERENCES mission (id),
    location_id     BIGINT REFERENCES location (id),
    categorie       VARCHAR(30) NOT NULL,
    sous_categorie  VARCHAR(40) NOT NULL,
    libelle         VARCHAR(255) NOT NULL,
    montant         NUMERIC(16, 2) NOT NULL,
    date_depense    DATE NOT NULL,
    fournisseur     VARCHAR(180),
    mode_paiement   VARCHAR(25),
    reference       VARCHAR(80),
    justificatif    VARCHAR(500),
    kilometrage     BIGINT,
    -- Detail des pleins de carburant (chapitre 18)
    station         VARCHAR(150),
    quantite_litres NUMERIC(12, 2),
    prix_litre      NUMERIC(12, 2),
    a_charge_client BOOLEAN NOT NULL DEFAULT FALSE,
    observations    TEXT,
    cree_le         TIMESTAMPTZ,
    cree_par        VARCHAR(100),
    modifie_le      TIMESTAMPTZ,
    modifie_par     VARCHAR(100),
    version         INTEGER,
    CONSTRAINT depense_montant_positif CHECK (montant >= 0),
    CONSTRAINT depense_carburant_quantite CHECK (quantite_litres IS NULL OR quantite_litres > 0)
);

CREATE INDEX idx_depense_camion ON depense (camion_id);
CREATE INDEX idx_depense_date ON depense (date_depense);
CREATE INDEX idx_depense_categorie ON depense (categorie);
CREATE INDEX idx_depense_sous_categorie ON depense (sous_categorie);
-- Les pleins sont lus par camion et par compteur pour calculer la consommation.
CREATE INDEX idx_depense_carburant ON depense (camion_id, kilometrage)
    WHERE categorie = 'CARBURANT';
