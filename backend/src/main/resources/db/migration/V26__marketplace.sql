-- =============================================================================
-- V26 : module Marketplace.
--
-- Chaque entreprise a une boutique en ligne publique (adresse /boutique/{slug})
-- et apparait sur la place de marche commune de la plateforme. Elle y publie
-- trois sortes d'offres : des articles de son point de vente (prix, photo et
-- stock lus en direct), des vehicules de son parc a louer, et des produits
-- saisis directement dans la Marketplace.
--
-- Le client commande sans compte (nom et telephone), paie au retrait / a la
-- livraison ou par mobile money, et suit sa commande par un lien a jeton.
-- Une demande de location de vehicule devient un contrat une fois acceptee.
-- =============================================================================

CREATE TABLE mkt_boutique (
    id                    BIGSERIAL PRIMARY KEY,
    entreprise_id         BIGINT NOT NULL REFERENCES entreprise (id),
    -- Adresse publique, unique sur toute la plateforme.
    slug                  VARCHAR(60) NOT NULL,
    nom                   VARCHAR(150) NOT NULL,
    slogan                VARCHAR(200),
    description           TEXT,
    categorie             VARCHAR(60),
    couverture            TEXT,
    telephone             VARCHAR(40),
    whatsapp              VARCHAR(40),
    email                 VARCHAR(150),
    adresse               VARCHAR(255),
    ville                 VARCHAR(80),
    horaires              VARCHAR(255),
    publiee               BOOLEAN NOT NULL DEFAULT FALSE,
    retrait_actif         BOOLEAN NOT NULL DEFAULT TRUE,
    livraison_active      BOOLEAN NOT NULL DEFAULT FALSE,
    frais_livraison       NUMERIC(16, 2),
    zone_livraison        VARCHAR(255),
    paiement_remise       BOOLEAN NOT NULL DEFAULT TRUE,
    paiement_mobile_money BOOLEAN NOT NULL DEFAULT FALSE,
    message_confirmation  VARCHAR(500),
    cree_le               TIMESTAMPTZ,
    cree_par              VARCHAR(100),
    modifie_le            TIMESTAMPTZ,
    modifie_par           VARCHAR(100),
    version               INTEGER,
    CONSTRAINT uk_mkt_boutique_entreprise UNIQUE (entreprise_id),
    CONSTRAINT uk_mkt_boutique_slug UNIQUE (slug)
);

CREATE TABLE mkt_offre (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    type          VARCHAR(20) NOT NULL,
    article_id    BIGINT REFERENCES pdv_article (id),
    camion_id     BIGINT REFERENCES camion (id),
    titre         VARCHAR(150) NOT NULL,
    description   TEXT,
    categorie     VARCHAR(80),
    -- Article du PDV : vide = prix de vente de l'article, lu en direct.
    prix          NUMERIC(16, 2),
    -- Vehicule : JOUR ; produit libre : unite de vente libre (piece, kg...).
    unite_prix    VARCHAR(20),
    caution       NUMERIC(16, 2),
    -- Article du PDV : vide = photo de l'article ; vehicule : photo du vehicule.
    image         TEXT,
    -- Produit libre : quantite disponible, vide = sans suivi.
    stock         INTEGER,
    publiee       BOOLEAN NOT NULL DEFAULT TRUE,
    mise_en_avant BOOLEAN NOT NULL DEFAULT FALSE,
    ordre         INTEGER NOT NULL DEFAULT 0,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT ck_mkt_offre_type CHECK (type IN ('ARTICLE', 'VEHICULE', 'LIBRE')),
    CONSTRAINT ck_mkt_offre_source CHECK (
        (type = 'ARTICLE' AND article_id IS NOT NULL)
        OR (type = 'VEHICULE' AND camion_id IS NOT NULL)
        OR (type = 'LIBRE')),
    CONSTRAINT ck_mkt_offre_prix CHECK (prix IS NULL OR prix >= 0)
);
CREATE INDEX idx_mkt_offre_entreprise ON mkt_offre (entreprise_id, publiee);

CREATE TABLE mkt_commande (
    id                BIGSERIAL PRIMARY KEY,
    entreprise_id     BIGINT NOT NULL REFERENCES entreprise (id),
    numero            VARCHAR(40) NOT NULL,
    -- Lien de suivi remis au client, imprevisible.
    jeton             VARCHAR(32) NOT NULL,
    client_id         BIGINT REFERENCES client (id),
    client_nom        VARCHAR(150) NOT NULL,
    client_telephone  VARCHAR(40) NOT NULL,
    client_email      VARCHAR(150),
    mode_remise       VARCHAR(20) NOT NULL,
    adresse_livraison VARCHAR(255),
    mode_paiement     VARCHAR(20) NOT NULL,
    statut_paiement   VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    reference_paiement VARCHAR(100),
    statut            VARCHAR(20) NOT NULL DEFAULT 'NOUVELLE',
    sous_total        NUMERIC(16, 2) NOT NULL,
    frais_livraison   NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total             NUMERIC(16, 2) NOT NULL,
    note              VARCHAR(500),
    motif_annulation  VARCHAR(255),
    date_commande     TIMESTAMPTZ NOT NULL,
    cree_le           TIMESTAMPTZ,
    cree_par          VARCHAR(100),
    modifie_le        TIMESTAMPTZ,
    modifie_par       VARCHAR(100),
    version           INTEGER,
    CONSTRAINT uk_mkt_commande_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT uk_mkt_commande_jeton UNIQUE (jeton),
    CONSTRAINT ck_mkt_commande_remise CHECK (mode_remise IN ('RETRAIT', 'LIVRAISON')),
    CONSTRAINT ck_mkt_commande_paiement CHECK (mode_paiement IN ('A_LA_REMISE', 'MOBILE_MONEY')),
    CONSTRAINT ck_mkt_commande_statut_paiement CHECK (statut_paiement IN ('EN_ATTENTE', 'PAYEE', 'ECHOUEE', 'REMBOURSEE')),
    CONSTRAINT ck_mkt_commande_statut CHECK (statut IN ('NOUVELLE', 'CONFIRMEE', 'PRETE', 'EN_LIVRAISON', 'LIVREE', 'ANNULEE'))
);
CREATE INDEX idx_mkt_commande_entreprise ON mkt_commande (entreprise_id, statut, date_commande DESC);

CREATE TABLE mkt_ligne_commande (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    commande_id   BIGINT NOT NULL REFERENCES mkt_commande (id) ON DELETE CASCADE,
    offre_id      BIGINT REFERENCES mkt_offre (id) ON DELETE SET NULL,
    article_id    BIGINT REFERENCES pdv_article (id),
    libelle       VARCHAR(150) NOT NULL,
    prix_unitaire NUMERIC(16, 2) NOT NULL,
    quantite      INTEGER NOT NULL,
    total         NUMERIC(16, 2) NOT NULL,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT ck_mkt_ligne_quantite CHECK (quantite > 0)
);

CREATE TABLE mkt_demande_location (
    id               BIGSERIAL PRIMARY KEY,
    entreprise_id    BIGINT NOT NULL REFERENCES entreprise (id),
    numero           VARCHAR(40) NOT NULL,
    jeton            VARCHAR(32) NOT NULL,
    offre_id         BIGINT REFERENCES mkt_offre (id) ON DELETE SET NULL,
    camion_id        BIGINT NOT NULL REFERENCES camion (id),
    client_id        BIGINT REFERENCES client (id),
    client_nom       VARCHAR(150) NOT NULL,
    client_telephone VARCHAR(40) NOT NULL,
    client_email     VARCHAR(150),
    date_debut       DATE NOT NULL,
    date_fin         DATE NOT NULL,
    tarif_jour       NUMERIC(16, 2) NOT NULL,
    montant_estime   NUMERIC(16, 2) NOT NULL,
    lieu_depart      VARCHAR(180),
    destination      VARCHAR(180),
    message          VARCHAR(500),
    statut           VARCHAR(20) NOT NULL DEFAULT 'NOUVELLE',
    motif_refus      VARCHAR(255),
    location_id      BIGINT REFERENCES location (id),
    date_demande     TIMESTAMPTZ NOT NULL,
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT uk_mkt_demande_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT uk_mkt_demande_jeton UNIQUE (jeton),
    CONSTRAINT ck_mkt_demande_dates CHECK (date_fin >= date_debut),
    CONSTRAINT ck_mkt_demande_statut CHECK (statut IN ('NOUVELLE', 'ACCEPTEE', 'REFUSEE', 'ANNULEE'))
);
CREATE INDEX idx_mkt_demande_entreprise ON mkt_demande_location (entreprise_id, statut, date_demande DESC);
