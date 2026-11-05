-- =============================================================================
-- V33 : point de vente « Quincaillerie & materiaux (BTP) ».
--
-- Troisieme type d'activite du point de vente, a cote de la boutique et du
-- restaurant. Il ajoute :
--   - les comptes clients (particuliers, artisans, entreprises du BTP) ;
--   - la vente a credit ou avec acompte, et les reglements recus ensuite ;
--   - les devis (pro forma) transformes en vente ;
--   - les conditionnements : un meme produit vendu au sac, a la tonne, a la
--     botte ou au carton, le stock restant tenu dans l'unite de base ;
--   - les reglages de tout cela, choisis par l'administrateur.
-- La boutique et le restaurant ne changent pas : les colonnes ajoutees aux
-- ventes prennent des valeurs neutres (tout est paye comptant).
-- =============================================================================

-- --------------------------------------------------------------- reglages
CREATE TABLE pdv_reglage_materiaux (
    id                      BIGSERIAL PRIMARY KEY,
    entreprise_id           BIGINT NOT NULL REFERENCES entreprise (id),
    credit_actif            BOOLEAN NOT NULL DEFAULT TRUE,
    credit_caissier         BOOLEAN NOT NULL DEFAULT TRUE,
    depassement_gerant      BOOLEAN NOT NULL DEFAULT TRUE,
    bloquer_si_retard       BOOLEAN NOT NULL DEFAULT TRUE,
    plafond_defaut          NUMERIC(16, 2),
    delai_paiement_jours    INTEGER NOT NULL DEFAULT 30,
    acompte_minimum         INTEGER NOT NULL DEFAULT 0,
    client_obligatoire      BOOLEAN NOT NULL DEFAULT FALSE,
    devis_actif             BOOLEAN NOT NULL DEFAULT TRUE,
    devis_validite_jours    INTEGER NOT NULL DEFAULT 30,
    mentions_devis          VARCHAR(500),
    facture_a4              BOOLEAN NOT NULL DEFAULT TRUE,
    tva_active              BOOLEAN NOT NULL DEFAULT TRUE,
    conditionnements_actifs BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le                 TIMESTAMPTZ,
    cree_par                VARCHAR(100),
    modifie_le              TIMESTAMPTZ,
    modifie_par             VARCHAR(100),
    version                 INTEGER,
    CONSTRAINT uk_pdv_reglage_materiaux UNIQUE (entreprise_id),
    CONSTRAINT ck_pdv_reglage_materiaux_delai CHECK (delai_paiement_jours BETWEEN 0 AND 365),
    CONSTRAINT ck_pdv_reglage_materiaux_acompte CHECK (acompte_minimum BETWEEN 0 AND 100),
    CONSTRAINT ck_pdv_reglage_materiaux_devis CHECK (devis_validite_jours BETWEEN 1 AND 365)
);

-- ---------------------------------------------------------------- clients
CREATE TABLE pdv_client (
    id                   BIGSERIAL PRIMARY KEY,
    entreprise_id        BIGINT NOT NULL REFERENCES entreprise (id),
    code                 VARCHAR(40) NOT NULL,
    type                 VARCHAR(20) NOT NULL DEFAULT 'PARTICULIER',
    nom                  VARCHAR(150) NOT NULL,
    telephone            VARCHAR(40),
    email                VARCHAR(150),
    adresse              VARCHAR(255),
    nif                  VARCHAR(60),
    -- Nul : le plafond par defaut des reglages ; zero : aucun credit.
    plafond_credit       NUMERIC(16, 2),
    -- Nul : le delai par defaut des reglages.
    delai_paiement_jours INTEGER,
    note                 VARCHAR(500),
    actif                BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le              TIMESTAMPTZ,
    cree_par             VARCHAR(100),
    modifie_le           TIMESTAMPTZ,
    modifie_par          VARCHAR(100),
    version              INTEGER,
    CONSTRAINT uk_pdv_client_code UNIQUE (entreprise_id, code),
    CONSTRAINT ck_pdv_client_type CHECK (type IN ('PARTICULIER', 'ARTISAN', 'ENTREPRISE')),
    CONSTRAINT ck_pdv_client_plafond CHECK (plafond_credit IS NULL OR plafond_credit >= 0)
);
CREATE INDEX idx_pdv_client_nom ON pdv_client (entreprise_id, lower(nom));

-- ------------------------------------------------- ventes a credit et acomptes
ALTER TABLE pdv_vente
    ADD COLUMN client_id   BIGINT REFERENCES pdv_client (id),
    -- Paye a l'encaissement (acompte ou totalite), dans le mode de paiement de la vente.
    ADD COLUMN montant_paye NUMERIC(16, 2),
    -- Reste du par le client, diminue par ses reglements.
    ADD COLUMN reste_du     NUMERIC(16, 2) NOT NULL DEFAULT 0,
    ADD COLUMN echeance     DATE,
    ADD COLUMN devis_id     BIGINT;
UPDATE pdv_vente SET montant_paye = total;
ALTER TABLE pdv_vente ALTER COLUMN montant_paye SET NOT NULL;
ALTER TABLE pdv_vente ADD CONSTRAINT ck_pdv_vente_reste CHECK (reste_du >= 0 AND montant_paye >= 0);
CREATE INDEX idx_pdv_vente_client ON pdv_vente (client_id) WHERE client_id IS NOT NULL;

-- Reglement d'un client, impute sur une de ses ventes a credit. Un versement
-- qui solde plusieurs ventes donne une ligne par vente, sous le meme numero.
CREATE TABLE pdv_reglement_client (
    id                 BIGSERIAL PRIMARY KEY,
    entreprise_id      BIGINT NOT NULL REFERENCES entreprise (id),
    numero             VARCHAR(40) NOT NULL,
    client_id          BIGINT NOT NULL REFERENCES pdv_client (id),
    vente_id           BIGINT NOT NULL REFERENCES pdv_vente (id),
    session_id         BIGINT NOT NULL REFERENCES pdv_session_caisse (id),
    montant            NUMERIC(16, 2) NOT NULL,
    mode_paiement      VARCHAR(20) NOT NULL,
    reference_paiement VARCHAR(80),
    date_reglement     TIMESTAMPTZ NOT NULL,
    caissier_nom       VARCHAR(150) NOT NULL,
    statut             VARCHAR(20) NOT NULL DEFAULT 'VALIDE',
    annule_le          TIMESTAMPTZ,
    annule_par         VARCHAR(150),
    motif_annulation   VARCHAR(255),
    cree_le            TIMESTAMPTZ,
    cree_par           VARCHAR(100),
    modifie_le         TIMESTAMPTZ,
    modifie_par        VARCHAR(100),
    version            INTEGER,
    CONSTRAINT ck_pdv_reglement_montant CHECK (montant > 0),
    CONSTRAINT ck_pdv_reglement_statut CHECK (statut IN ('VALIDE', 'ANNULE'))
);
CREATE INDEX idx_pdv_reglement_client ON pdv_reglement_client (client_id, date_reglement DESC);
CREATE INDEX idx_pdv_reglement_vente ON pdv_reglement_client (vente_id);
CREATE INDEX idx_pdv_reglement_session ON pdv_reglement_client (session_id);
CREATE INDEX idx_pdv_reglement_numero ON pdv_reglement_client (entreprise_id, numero);

-- --------------------------------------------------------- conditionnements
-- Le produit garde son unite de base (sac, barre, kg) qui porte le stock ;
-- chaque conditionnement en contient un nombre fixe et a son propre prix.
CREATE TABLE pdv_conditionnement (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    article_id    BIGINT NOT NULL REFERENCES pdv_article (id),
    nom           VARCHAR(40) NOT NULL,
    coefficient   NUMERIC(14, 3) NOT NULL,
    prix_vente    NUMERIC(16, 2) NOT NULL,
    code_barres   VARCHAR(60),
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT ck_pdv_conditionnement_coef CHECK (coefficient > 0),
    CONSTRAINT ck_pdv_conditionnement_prix CHECK (prix_vente >= 0)
);
CREATE INDEX idx_pdv_conditionnement_article ON pdv_conditionnement (article_id);
CREATE UNIQUE INDEX uk_pdv_conditionnement_code_barres ON pdv_conditionnement (entreprise_id, code_barres)
    WHERE code_barres IS NOT NULL;

ALTER TABLE pdv_ligne_vente
    ADD COLUMN conditionnement_id BIGINT REFERENCES pdv_conditionnement (id),
    -- Quantite sortie du stock, dans l'unite de base du produit.
    ADD COLUMN quantite_base      NUMERIC(14, 3);
UPDATE pdv_ligne_vente SET quantite_base = quantite;
ALTER TABLE pdv_ligne_vente ALTER COLUMN quantite_base SET NOT NULL;

-- ------------------------------------------------------------------- devis
CREATE TABLE pdv_devis (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    numero        VARCHAR(40) NOT NULL,
    client_id     BIGINT REFERENCES pdv_client (id),
    -- Client de passage, sans fiche : son nom seulement.
    client_nom    VARCHAR(150),
    date_devis    DATE NOT NULL,
    valable_jusqu DATE NOT NULL,
    statut        VARCHAR(20) NOT NULL DEFAULT 'EN_COURS',
    total_brut    NUMERIC(16, 2) NOT NULL,
    remise        NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total         NUMERIC(16, 2) NOT NULL,
    note          VARCHAR(500),
    vendeur_nom   VARCHAR(150) NOT NULL,
    vente_id      BIGINT REFERENCES pdv_vente (id),
    annule_le     TIMESTAMPTZ,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_devis_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_devis_statut CHECK (statut IN ('EN_COURS', 'CONVERTI', 'ANNULE'))
);
CREATE INDEX idx_pdv_devis_date ON pdv_devis (entreprise_id, date_devis DESC);
CREATE INDEX idx_pdv_devis_client ON pdv_devis (client_id);
ALTER TABLE pdv_vente ADD CONSTRAINT fk_pdv_vente_devis FOREIGN KEY (devis_id) REFERENCES pdv_devis (id);

CREATE TABLE pdv_ligne_devis (
    id                 BIGSERIAL PRIMARY KEY,
    entreprise_id      BIGINT NOT NULL REFERENCES entreprise (id),
    devis_id           BIGINT NOT NULL REFERENCES pdv_devis (id) ON DELETE CASCADE,
    article_id         BIGINT NOT NULL REFERENCES pdv_article (id),
    conditionnement_id BIGINT REFERENCES pdv_conditionnement (id),
    libelle            VARCHAR(150) NOT NULL,
    prix_unitaire      NUMERIC(16, 2) NOT NULL,
    quantite           NUMERIC(14, 3) NOT NULL,
    remise             NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total              NUMERIC(16, 2) NOT NULL,
    cree_le            TIMESTAMPTZ,
    cree_par           VARCHAR(100),
    modifie_le         TIMESTAMPTZ,
    modifie_par        VARCHAR(100),
    version            INTEGER,
    CONSTRAINT ck_pdv_ligne_devis_quantite CHECK (quantite > 0)
);
CREATE INDEX idx_pdv_ligne_devis ON pdv_ligne_devis (devis_id);
