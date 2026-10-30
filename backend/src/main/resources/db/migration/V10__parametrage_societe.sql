-- Identite de l'entreprise : entete des factures, des rapports, et page de connexion.
CREATE TABLE parametre_societe (
    id               BIGSERIAL PRIMARY KEY,
    nom              VARCHAR(150) NOT NULL,
    sigle            VARCHAR(60),
    forme_juridique  VARCHAR(60),
    adresse          VARCHAR(255),
    ville            VARCHAR(100),
    pays             VARCHAR(100),
    telephone        VARCHAR(40),
    email            VARCHAR(150),
    site_web         VARCHAR(150),
    nif              VARCHAR(60),
    rccm             VARCHAR(60),
    banque           VARCHAR(120),
    compte_bancaire  VARCHAR(60),
    mentions_facture VARCHAR(500),
    logo_base64      TEXT,
    logo_type        VARCHAR(60),
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER
);

-- Une seule entreprise se parametre : l'index l'impose au niveau de la base.
CREATE UNIQUE INDEX ux_parametre_societe_unique ON parametre_societe ((TRUE));
