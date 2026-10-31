-- =============================================================================
-- V14 : module Point de vente.
--
-- Catalogue d'articles, stock et ses mouvements, sessions de caisse, ventes et
-- leurs lignes. Toutes les tables sont cloisonnees par entreprise, comme les
-- donnees du module Gestion de parc.
-- =============================================================================

CREATE TABLE pdv_categorie (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    nom           VARCHAR(80) NOT NULL,
    couleur       VARCHAR(9),
    ordre         INTEGER NOT NULL DEFAULT 0,
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_categorie_nom UNIQUE (entreprise_id, nom)
);

CREATE TABLE pdv_article (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    code          VARCHAR(40) NOT NULL,
    code_barres   VARCHAR(60),
    nom           VARCHAR(150) NOT NULL,
    categorie_id  BIGINT REFERENCES pdv_categorie (id),
    prix_vente    NUMERIC(16, 2) NOT NULL,
    prix_achat    NUMERIC(16, 2),
    unite         VARCHAR(20),
    suivi_stock   BOOLEAN NOT NULL DEFAULT TRUE,
    stock_actuel  NUMERIC(14, 3) NOT NULL DEFAULT 0,
    seuil_alerte  NUMERIC(14, 3),
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_article_code UNIQUE (entreprise_id, code),
    CONSTRAINT ck_pdv_article_prix CHECK (prix_vente >= 0)
);
CREATE UNIQUE INDEX uk_pdv_article_code_barres ON pdv_article (entreprise_id, code_barres)
    WHERE code_barres IS NOT NULL;
CREATE INDEX idx_pdv_article_categorie ON pdv_article (categorie_id);
CREATE INDEX idx_pdv_article_entreprise ON pdv_article (entreprise_id, actif);

CREATE TABLE pdv_session_caisse (
    id                BIGSERIAL PRIMARY KEY,
    entreprise_id     BIGINT NOT NULL REFERENCES entreprise (id),
    numero            VARCHAR(40) NOT NULL,
    caissier_id       BIGINT NOT NULL REFERENCES utilisateur (id),
    caissier_nom      VARCHAR(150) NOT NULL,
    statut            VARCHAR(20) NOT NULL,
    ouverte_le        TIMESTAMPTZ NOT NULL,
    fond_initial      NUMERIC(16, 2) NOT NULL DEFAULT 0,
    fermee_le         TIMESTAMPTZ,
    montant_compte    NUMERIC(16, 2),
    montant_theorique NUMERIC(16, 2),
    ecart             NUMERIC(16, 2),
    observations      TEXT,
    cree_le           TIMESTAMPTZ,
    cree_par          VARCHAR(100),
    modifie_le        TIMESTAMPTZ,
    modifie_par       VARCHAR(100),
    version           INTEGER,
    CONSTRAINT uk_pdv_session_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_session_statut CHECK (statut IN ('OUVERTE', 'FERMEE'))
);
-- Un caissier ne tient qu'une caisse a la fois.
CREATE UNIQUE INDEX uk_pdv_session_ouverte ON pdv_session_caisse (caissier_id) WHERE statut = 'OUVERTE';
CREATE INDEX idx_pdv_session_entreprise ON pdv_session_caisse (entreprise_id, ouverte_le DESC);

CREATE TABLE pdv_vente (
    id                 BIGSERIAL PRIMARY KEY,
    entreprise_id      BIGINT NOT NULL REFERENCES entreprise (id),
    numero             VARCHAR(40) NOT NULL,
    session_id         BIGINT NOT NULL REFERENCES pdv_session_caisse (id),
    date_vente         TIMESTAMPTZ NOT NULL,
    caissier_nom       VARCHAR(150) NOT NULL,
    client_nom         VARCHAR(150),
    total_brut         NUMERIC(16, 2) NOT NULL,
    remise             NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total              NUMERIC(16, 2) NOT NULL,
    mode_paiement      VARCHAR(20) NOT NULL,
    montant_recu       NUMERIC(16, 2),
    monnaie_rendue     NUMERIC(16, 2),
    reference_paiement VARCHAR(80),
    statut             VARCHAR(20) NOT NULL,
    annulee_le         TIMESTAMPTZ,
    annulee_par        VARCHAR(100),
    motif_annulation   VARCHAR(255),
    cree_le            TIMESTAMPTZ,
    cree_par           VARCHAR(100),
    modifie_le         TIMESTAMPTZ,
    modifie_par        VARCHAR(100),
    version            INTEGER,
    CONSTRAINT uk_pdv_vente_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_vente_statut CHECK (statut IN ('VALIDEE', 'ANNULEE'))
);
CREATE INDEX idx_pdv_vente_date ON pdv_vente (entreprise_id, date_vente DESC);
CREATE INDEX idx_pdv_vente_session ON pdv_vente (session_id);

CREATE TABLE pdv_ligne_vente (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    vente_id      BIGINT NOT NULL REFERENCES pdv_vente (id) ON DELETE CASCADE,
    article_id    BIGINT NOT NULL REFERENCES pdv_article (id),
    libelle       VARCHAR(150) NOT NULL,
    prix_unitaire NUMERIC(16, 2) NOT NULL,
    quantite      NUMERIC(14, 3) NOT NULL,
    remise        NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total         NUMERIC(16, 2) NOT NULL,
    -- Cout d'achat au moment de la vente : la marge ne bouge pas quand le cout evolue.
    prix_achat    NUMERIC(16, 2),
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT ck_pdv_ligne_quantite CHECK (quantite > 0)
);
CREATE INDEX idx_pdv_ligne_vente ON pdv_ligne_vente (vente_id);
CREATE INDEX idx_pdv_ligne_article ON pdv_ligne_vente (article_id);

CREATE TABLE pdv_mouvement_stock (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    article_id     BIGINT NOT NULL REFERENCES pdv_article (id),
    type           VARCHAR(20) NOT NULL,
    quantite       NUMERIC(14, 3) NOT NULL,
    stock_apres    NUMERIC(14, 3) NOT NULL,
    cout_unitaire  NUMERIC(16, 2),
    date_mouvement TIMESTAMPTZ NOT NULL,
    motif          VARCHAR(255),
    vente_id       BIGINT REFERENCES pdv_vente (id),
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT ck_pdv_mouvement_type CHECK (type IN ('ENTREE', 'SORTIE', 'PERTE', 'INVENTAIRE', 'VENTE', 'RETOUR'))
);
CREATE INDEX idx_pdv_mouvement_article ON pdv_mouvement_stock (article_id, date_mouvement DESC);
CREATE INDEX idx_pdv_mouvement_entreprise ON pdv_mouvement_stock (entreprise_id, date_mouvement DESC);

-- Profils du point de vente.
ALTER TABLE utilisateur_role DROP CONSTRAINT IF EXISTS ck_utilisateur_role_valeur;
ALTER TABLE utilisateur_role
    ADD CONSTRAINT ck_utilisateur_role_valeur
        CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'GESTIONNAIRE_PARC', 'CHAUFFEUR', 'GERANT', 'CAISSIER'));
