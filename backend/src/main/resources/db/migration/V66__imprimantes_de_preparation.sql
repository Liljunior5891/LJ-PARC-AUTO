-- Imprimantes de preparation : un poste (ancien « ecran de preparation ») peut
-- afficher ses articles sur un ecran, les imprimer sur une imprimante de cuisine,
-- ou les deux. Chaque validation par la caisse cree un bon par imprimante dans la
-- file d'impression ; la caisse l'imprime par le navigateur (et, plus tard, un
-- agent installe dans le restaurant l'enverra directement a l'imprimante reseau).

CREATE TABLE pdv_imprimante (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    nom            VARCHAR(80) NOT NULL,
    mode           VARCHAR(20) NOT NULL DEFAULT 'NAVIGATEUR',
    adresse_ip     VARCHAR(64),
    port           INTEGER NOT NULL DEFAULT 9100,
    largeur_papier INTEGER NOT NULL DEFAULT 80,
    copies         INTEGER NOT NULL DEFAULT 1,
    coupe          BOOLEAN NOT NULL DEFAULT TRUE,
    bip            BOOLEAN NOT NULL DEFAULT FALSE,
    -- Caisse qui imprime les bons par le navigateur ; nulle : toutes les caisses.
    caisse_id      BIGINT REFERENCES pdv_caisse (id) ON DELETE SET NULL,
    actif          BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT uk_pdv_imprimante_nom UNIQUE (entreprise_id, nom),
    CONSTRAINT ck_pdv_imprimante_mode CHECK (mode IN ('NAVIGATEUR', 'AGENT')),
    CONSTRAINT ck_pdv_imprimante_largeur CHECK (largeur_papier IN (58, 80)),
    CONSTRAINT ck_pdv_imprimante_copies CHECK (copies BETWEEN 1 AND 5),
    CONSTRAINT ck_pdv_imprimante_port CHECK (port BETWEEN 1 AND 65535)
);

ALTER TABLE pdv_ecran_preparation ADD COLUMN afficher_ecran BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE pdv_ecran_preparation ADD COLUMN imprimante_id BIGINT REFERENCES pdv_imprimante (id) ON DELETE SET NULL;

-- Bon a imprimer : son contenu (JSON) est fige a la creation.
CREATE TABLE pdv_travail_impression (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    imprimante_id  BIGINT NOT NULL REFERENCES pdv_imprimante (id) ON DELETE CASCADE,
    type           VARCHAR(20) NOT NULL,
    statut         VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    commande_id    BIGINT REFERENCES pdv_commande (id) ON DELETE SET NULL,
    envoi          INTEGER,
    vente_id       BIGINT REFERENCES pdv_vente (id) ON DELETE SET NULL,
    reference      VARCHAR(80) NOT NULL,
    contenu        TEXT NOT NULL,
    tentatives     INTEGER NOT NULL DEFAULT 0,
    erreur         VARCHAR(255),
    imprime_le     TIMESTAMPTZ,
    imprime_par    VARCHAR(150),
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT ck_pdv_travail_impression_type CHECK (type IN ('BON', 'ANNULATION', 'DUPLICATA', 'TEST')),
    CONSTRAINT ck_pdv_travail_impression_statut CHECK (statut IN ('EN_ATTENTE', 'EN_COURS', 'IMPRIME', 'ECHEC'))
);
CREATE INDEX idx_pdv_travail_impression_attente ON pdv_travail_impression (imprimante_id, statut)
    WHERE statut IN ('EN_ATTENTE', 'EN_COURS', 'ECHEC');
CREATE INDEX idx_pdv_travail_impression_commande ON pdv_travail_impression (commande_id);
CREATE INDEX idx_pdv_travail_impression_vente ON pdv_travail_impression (vente_id);

-- Note par article saisie a la caisse (« sans oignon »), imprimee sur le bon de cuisine.
ALTER TABLE pdv_ligne_vente ADD COLUMN note VARCHAR(160);
