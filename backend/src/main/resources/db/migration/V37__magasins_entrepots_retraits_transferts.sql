-- Quincaillerie : magasins et entrepots. Sans entrepot actif, rien de tout ceci n'entre en jeu :
-- pdv_article.stock_actuel et stock_reserve restent la seule reference. Avec un entrepot, ils portent
-- le total de l'entreprise (somme des lieux, plus ce qui est en transit).

CREATE TABLE pdv_lieu_stock (
    id              BIGSERIAL PRIMARY KEY,
    entreprise_id   BIGINT NOT NULL REFERENCES entreprise (id),
    nom             VARCHAR(80) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    adresse         VARCHAR(255),
    telephone       VARCHAR(40),
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    -- Recoit le stock existant a l'activation et tout mouvement sans lieu precis.
    principal       BOOLEAN NOT NULL DEFAULT FALSE,
    visible_en_ligne BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le         TIMESTAMPTZ,
    cree_par        VARCHAR(100),
    modifie_le      TIMESTAMPTZ,
    modifie_par     VARCHAR(100),
    version         INTEGER,
    CONSTRAINT uk_pdv_lieu_stock_nom UNIQUE (entreprise_id, nom),
    CONSTRAINT ck_pdv_lieu_stock_type CHECK (type IN ('MAGASIN', 'ENTREPOT'))
);
CREATE UNIQUE INDEX uk_pdv_lieu_stock_principal ON pdv_lieu_stock (entreprise_id) WHERE principal;

CREATE TABLE pdv_lieu_utilisateur (
    lieu_id        BIGINT NOT NULL REFERENCES pdv_lieu_stock (id) ON DELETE CASCADE,
    utilisateur_id BIGINT NOT NULL,
    PRIMARY KEY (lieu_id, utilisateur_id)
);

CREATE TABLE pdv_stock_lieu (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    lieu_id       BIGINT NOT NULL REFERENCES pdv_lieu_stock (id),
    article_id    BIGINT NOT NULL REFERENCES pdv_article (id),
    stock         NUMERIC(14, 3) NOT NULL DEFAULT 0,
    reserve       NUMERIC(14, 3) NOT NULL DEFAULT 0,
    seuil_alerte  NUMERIC(14, 3),
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_stock_lieu UNIQUE (lieu_id, article_id),
    CONSTRAINT ck_pdv_stock_lieu_stock CHECK (stock >= 0),
    CONSTRAINT ck_pdv_stock_lieu_reserve CHECK (reserve >= 0)
);
CREATE INDEX idx_pdv_stock_lieu_article ON pdv_stock_lieu (article_id);

-- Les caisses vendent depuis un magasin, les lignes se retirent dans un lieu.
ALTER TABLE pdv_caisse ADD COLUMN lieu_id BIGINT REFERENCES pdv_lieu_stock (id);
ALTER TABLE pdv_ligne_vente ADD COLUMN lieu_id BIGINT REFERENCES pdv_lieu_stock (id);
ALTER TABLE pdv_bon_livraison ADD COLUMN lieu_id BIGINT REFERENCES pdv_lieu_stock (id);
ALTER TABLE pdv_commande_fournisseur ADD COLUMN lieu_id BIGINT REFERENCES pdv_lieu_stock (id);

ALTER TABLE pdv_mouvement_stock
    ADD COLUMN lieu_id BIGINT REFERENCES pdv_lieu_stock (id),
    ADD COLUMN stock_lieu_apres NUMERIC(14, 3);
ALTER TABLE pdv_mouvement_stock DROP CONSTRAINT ck_pdv_mouvement_type;
ALTER TABLE pdv_mouvement_stock ADD CONSTRAINT ck_pdv_mouvement_type CHECK (type IN (
    'ENTREE', 'SORTIE', 'PERTE', 'INVENTAIRE', 'VENTE', 'RETOUR', 'TRANSFERT_SORTIE', 'TRANSFERT_ENTREE'));
CREATE INDEX idx_pdv_mouvement_vente ON pdv_mouvement_stock (vente_id) WHERE vente_id IS NOT NULL;

-- Bon de retrait : presente par le porteur au lieu indique. Il n'expire jamais.
CREATE TABLE pdv_bon_retrait (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    numero        VARCHAR(40) NOT NULL,
    vente_id      BIGINT NOT NULL REFERENCES pdv_vente (id),
    lieu_id       BIGINT NOT NULL REFERENCES pdv_lieu_stock (id),
    date_emission TIMESTAMPTZ NOT NULL,
    statut        VARCHAR(20) NOT NULL DEFAULT 'A_SERVIR',
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_bon_retrait_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_bon_retrait_statut CHECK (statut IN ('A_SERVIR', 'SERVI', 'ANNULE'))
);
CREATE INDEX idx_pdv_bon_retrait_vente ON pdv_bon_retrait (vente_id);
CREATE INDEX idx_pdv_bon_retrait_lieu ON pdv_bon_retrait (lieu_id, statut, date_emission);

-- Remises faites sans connexion : la cle rend l'envoi repetable, un conflit attend le gerant.
CREATE TABLE pdv_remise_hors_ligne (
    id               BIGSERIAL PRIMARY KEY,
    entreprise_id    BIGINT NOT NULL REFERENCES entreprise (id),
    cle              VARCHAR(64) NOT NULL,
    bon_retrait_id   BIGINT NOT NULL REFERENCES pdv_bon_retrait (id),
    bon_livraison_id BIGINT REFERENCES pdv_bon_livraison (id),
    statut           VARCHAR(20) NOT NULL,
    detail           VARCHAR(1000),
    auteur           VARCHAR(150) NOT NULL,
    faite_le         TIMESTAMPTZ NOT NULL,
    recu_par         VARCHAR(150),
    traitee_par      VARCHAR(150),
    traitee_le       TIMESTAMPTZ,
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT uk_pdv_remise_hors_ligne UNIQUE (entreprise_id, cle),
    CONSTRAINT ck_pdv_remise_hors_ligne CHECK (statut IN ('ENREGISTREE', 'CONFLIT', 'TRAITEE'))
);

CREATE TABLE pdv_transfert (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    numero        VARCHAR(40) NOT NULL,
    source_id     BIGINT NOT NULL REFERENCES pdv_lieu_stock (id),
    destination_id BIGINT NOT NULL REFERENCES pdv_lieu_stock (id),
    statut        VARCHAR(20) NOT NULL,
    note          VARCHAR(255),
    demande_le    TIMESTAMPTZ NOT NULL,
    demande_par   VARCHAR(150) NOT NULL,
    valide_le     TIMESTAMPTZ,
    valide_par    VARCHAR(150),
    expedie_le    TIMESTAMPTZ,
    expedie_par   VARCHAR(150),
    transporteur  VARCHAR(150),
    recu_le       TIMESTAMPTZ,
    recu_par      VARCHAR(150),
    annule_le     TIMESTAMPTZ,
    annule_par    VARCHAR(150),
    motif         VARCHAR(255),
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_transfert_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_transfert_lieux CHECK (source_id <> destination_id),
    CONSTRAINT ck_pdv_transfert_statut CHECK (statut IN ('DEMANDE', 'VALIDE', 'EXPEDIE', 'ECART', 'RECU', 'ANNULE'))
);
CREATE INDEX idx_pdv_transfert_statut ON pdv_transfert (entreprise_id, statut);

CREATE TABLE pdv_ligne_transfert (
    id               BIGSERIAL PRIMARY KEY,
    entreprise_id    BIGINT NOT NULL REFERENCES entreprise (id),
    transfert_id     BIGINT NOT NULL REFERENCES pdv_transfert (id) ON DELETE CASCADE,
    article_id       BIGINT NOT NULL REFERENCES pdv_article (id),
    quantite         NUMERIC(14, 3) NOT NULL,
    quantite_expediee NUMERIC(14, 3),
    quantite_recue   NUMERIC(14, 3),
    -- Ecart reconnu par le gerant : perdu, revenu a la source ou finalement arrive.
    decision_ecart   VARCHAR(20),
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT ck_pdv_ligne_transfert_quantite CHECK (quantite > 0)
);
CREATE INDEX idx_pdv_ligne_transfert ON pdv_ligne_transfert (transfert_id);

ALTER TABLE utilisateur_role DROP CONSTRAINT IF EXISTS ck_utilisateur_role_valeur;
ALTER TABLE utilisateur_role ADD CONSTRAINT ck_utilisateur_role_valeur
    CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'GESTIONNAIRE_PARC', 'CHAUFFEUR', 'GERANT', 'CAISSIER',
                    'SERVEUR', 'PREPARATEUR', 'MAGASINIER'));
