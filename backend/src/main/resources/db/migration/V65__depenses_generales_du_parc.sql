-- Gestion de parc : charges generales (sans vehicule), categories modifiables,
-- depenses recurrentes, justificatif, annulation avec motif, fournisseurs et
-- dettes, validation par l'administrateur (reglage).

-- ------------------------------------------------------------------ reglage
CREATE TABLE parc_reglage (
    id                  BIGSERIAL PRIMARY KEY,
    entreprise_id       BIGINT NOT NULL REFERENCES entreprise (id),
    -- Active, une depense saisie par le gestionnaire attend l'administrateur avant de compter.
    validation_depenses BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT uk_parc_reglage_entreprise UNIQUE (entreprise_id)
);

-- ------------------------------------------------- categories des charges
CREATE TABLE parc_categorie_charge (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    nom           VARCHAR(80) NOT NULL,
    icone         VARCHAR(16),
    ordre         INTEGER NOT NULL DEFAULT 0,
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER
);
CREATE UNIQUE INDEX uk_parc_categorie_charge_nom ON parc_categorie_charge (entreprise_id, lower(nom));

-- -------------------------------------------------------------- fournisseurs
CREATE TABLE parc_fournisseur (
    id                    BIGSERIAL PRIMARY KEY,
    entreprise_id         BIGINT NOT NULL REFERENCES entreprise (id),
    nom                   VARCHAR(180) NOT NULL,
    -- Garage, station, assureur, pieces... (texte libre)
    activite              VARCHAR(80),
    contact               VARCHAR(150),
    telephone             VARCHAR(40),
    email                 VARCHAR(150),
    adresse               VARCHAR(255),
    nif                   VARCHAR(60),
    delai_paiement_jours  INTEGER NOT NULL DEFAULT 0,
    note                  VARCHAR(500),
    actif                 BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le               TIMESTAMPTZ,
    cree_par              VARCHAR(100),
    modifie_le            TIMESTAMPTZ,
    modifie_par           VARCHAR(100),
    version               INTEGER,
    CONSTRAINT ck_parc_fournisseur_delai CHECK (delai_paiement_jours BETWEEN 0 AND 365)
);
CREATE UNIQUE INDEX uk_parc_fournisseur_nom ON parc_fournisseur (entreprise_id, lower(nom));

-- ------------------------------------------------------ depenses recurrentes
CREATE TABLE parc_depense_recurrente (
    id                  BIGSERIAL PRIMARY KEY,
    entreprise_id       BIGINT NOT NULL REFERENCES entreprise (id),
    libelle             VARCHAR(150) NOT NULL,
    -- Vehicule et nature, ou categorie de charge generale.
    camion_id           BIGINT REFERENCES camion (id),
    sous_categorie      VARCHAR(40),
    categorie_charge_id BIGINT REFERENCES parc_categorie_charge (id),
    fournisseur_id      BIGINT REFERENCES parc_fournisseur (id),
    montant             NUMERIC(16, 2) NOT NULL,
    jour_echeance       INTEGER NOT NULL DEFAULT 1,
    mode_paiement       VARCHAR(25),
    debut               DATE NOT NULL,
    fin                 DATE,
    actif               BOOLEAN NOT NULL DEFAULT TRUE,
    note                VARCHAR(255),
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT ck_parc_recurrente_montant CHECK (montant > 0),
    CONSTRAINT ck_parc_recurrente_jour CHECK (jour_echeance BETWEEN 1 AND 28),
    CONSTRAINT ck_parc_recurrente_cible CHECK (
        (camion_id IS NOT NULL AND sous_categorie IS NOT NULL AND categorie_charge_id IS NULL)
        OR (camion_id IS NULL AND categorie_charge_id IS NOT NULL))
);

-- ------------------------------------------------------------------ depense
ALTER TABLE depense ALTER COLUMN camion_id DROP NOT NULL;
ALTER TABLE depense ADD COLUMN categorie_charge_id BIGINT REFERENCES parc_categorie_charge (id);
ALTER TABLE depense ADD COLUMN fournisseur_id BIGINT REFERENCES parc_fournisseur (id);
ALTER TABLE depense ADD COLUMN recurrente_id BIGINT REFERENCES parc_depense_recurrente (id);
ALTER TABLE depense ADD COLUMN periode VARCHAR(7);
-- Depense a payer : elle est la dette envers le fournisseur, soldee par les reglements.
ALTER TABLE depense ADD COLUMN a_payer BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE depense ADD COLUMN echeance DATE;
ALTER TABLE depense ADD COLUMN justificatif_chemin VARCHAR(255);
ALTER TABLE depense ADD COLUMN justificatif_nom VARCHAR(255);
ALTER TABLE depense ADD COLUMN justificatif_type VARCHAR(100);
ALTER TABLE depense ADD COLUMN statut VARCHAR(20) NOT NULL DEFAULT 'VALIDEE';
ALTER TABLE depense ADD COLUMN auteur_nom VARCHAR(150);
ALTER TABLE depense ADD COLUMN approuvee_par VARCHAR(150);
ALTER TABLE depense ADD COLUMN approuvee_le TIMESTAMPTZ;
ALTER TABLE depense ADD COLUMN refusee_par VARCHAR(150);
ALTER TABLE depense ADD COLUMN refusee_le TIMESTAMPTZ;
ALTER TABLE depense ADD COLUMN motif_refus VARCHAR(255);
ALTER TABLE depense ADD COLUMN annulee_par VARCHAR(150);
ALTER TABLE depense ADD COLUMN annulee_le TIMESTAMPTZ;
ALTER TABLE depense ADD COLUMN motif_annulation VARCHAR(255);
ALTER TABLE depense ADD CONSTRAINT ck_depense_statut CHECK (statut IN ('ATTENTE_ADMIN', 'VALIDEE', 'REFUSEE', 'ANNULEE'));
-- Une depense porte sur un vehicule, sauf une charge generale qui a sa categorie.
ALTER TABLE depense ADD CONSTRAINT ck_depense_cible CHECK (
    (categorie = 'CHARGE_GENERALE' AND camion_id IS NULL AND categorie_charge_id IS NOT NULL)
    OR (categorie <> 'CHARGE_GENERALE' AND camion_id IS NOT NULL AND categorie_charge_id IS NULL));
CREATE INDEX idx_depense_statut ON depense (statut) WHERE statut <> 'VALIDEE';
CREATE INDEX idx_depense_fournisseur ON depense (fournisseur_id);
-- Un mois d'une depense recurrente ne se paie qu'une fois (hors refusees et annulees).
CREATE UNIQUE INDEX uk_depense_recurrente_mois ON depense (recurrente_id, periode)
    WHERE recurrente_id IS NOT NULL AND statut IN ('VALIDEE', 'ATTENTE_ADMIN');

-- -------------------------------------------------- reglements des dettes
CREATE TABLE parc_reglement_fournisseur (
    id               BIGSERIAL PRIMARY KEY,
    entreprise_id    BIGINT NOT NULL REFERENCES entreprise (id),
    numero           VARCHAR(40) NOT NULL,
    depense_id       BIGINT NOT NULL REFERENCES depense (id),
    montant          NUMERIC(16, 2) NOT NULL,
    date_reglement   DATE NOT NULL,
    mode_paiement    VARCHAR(25),
    reference        VARCHAR(80),
    note             VARCHAR(255),
    auteur_nom       VARCHAR(150) NOT NULL,
    statut           VARCHAR(20) NOT NULL DEFAULT 'VALIDE',
    annule_le        TIMESTAMPTZ,
    annule_par       VARCHAR(150),
    motif_annulation VARCHAR(255),
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT uk_parc_reglement_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_parc_reglement_montant CHECK (montant > 0),
    CONSTRAINT ck_parc_reglement_statut CHECK (statut IN ('VALIDE', 'ANNULE'))
);
CREATE INDEX idx_parc_reglement_depense ON parc_reglement_fournisseur (depense_id);

-- ------------------------------- fiches pour les fournisseurs deja saisis
INSERT INTO parc_fournisseur (entreprise_id, nom, cree_le, cree_par, version)
SELECT DISTINCT ON (entreprise_id, lower(nom)) entreprise_id, nom, now(), 'migration', 0
FROM (
    SELECT entreprise_id, trim(fournisseur) AS nom FROM depense WHERE trim(coalesce(fournisseur, '')) <> ''
    UNION ALL
    SELECT entreprise_id, trim(fournisseur) AS nom FROM entretien WHERE trim(coalesce(fournisseur, '')) <> ''
) noms
ORDER BY entreprise_id, lower(nom), nom;

UPDATE depense d SET fournisseur_id = f.id
FROM parc_fournisseur f
WHERE f.entreprise_id = d.entreprise_id AND lower(f.nom) = lower(trim(d.fournisseur));
