-- =====================================================================
-- PARC AUTO - V6 : le camion est identifie par sa plaque d'immatriculation
--
-- Le numero interne faisait doublon : la plaque identifie deja le vehicule
-- de maniere unique et c'est elle que les equipes utilisent sur le terrain.
-- =====================================================================

ALTER TABLE camion DROP COLUMN numero_interne;

-- ---------------------------------------------------------------------
-- Marques de camions, avec leur logo (referentiel)
-- ---------------------------------------------------------------------
CREATE TABLE marque_camion (
    id          BIGSERIAL PRIMARY KEY,
    nom         VARCHAR(80) NOT NULL UNIQUE,
    logo_url    VARCHAR(500),
    couleur     VARCHAR(20),
    pays        VARCHAR(80),
    ordre       INTEGER NOT NULL DEFAULT 100,
    actif       BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le     TIMESTAMPTZ,
    cree_par    VARCHAR(100),
    modifie_le  TIMESTAMPTZ,
    modifie_par VARCHAR(100),
    version     INTEGER
);

CREATE INDEX idx_marque_actif ON marque_camion (actif, ordre);

-- Le camion reference la marque du referentiel ; la colonne texte reste
-- renseignee pour les marques hors liste.
ALTER TABLE camion ADD COLUMN marque_id BIGINT REFERENCES marque_camion (id);

-- ---------------------------------------------------------------------
-- Marques les plus repandues en Afrique de l'Ouest.
-- La couleur sert de pastille tant qu'aucun fichier de logo n'est fourni.
-- ---------------------------------------------------------------------
INSERT INTO marque_camion (nom, couleur, pays, ordre, cree_par, cree_le) VALUES
    ('Mercedes-Benz', '#00adef', 'Allemagne', 10, 'flyway', now()),
    ('Volvo',         '#003057', 'Suede',     20, 'flyway', now()),
    ('Scania',        '#041e42', 'Suede',     30, 'flyway', now()),
    ('MAN',           '#e2001a', 'Allemagne', 40, 'flyway', now()),
    ('Renault Trucks', '#e30613', 'France',   50, 'flyway', now()),
    ('Iveco',         '#004a8f', 'Italie',    60, 'flyway', now()),
    ('DAF',           '#009fe3', 'Pays-Bas',  70, 'flyway', now()),
    ('Isuzu',         '#c8102e', 'Japon',     80, 'flyway', now()),
    ('Hino',          '#e60012', 'Japon',     90, 'flyway', now()),
    ('Mitsubishi Fuso', '#e60012', 'Japon',  100, 'flyway', now()),
    ('Toyota',        '#eb0a1e', 'Japon',    110, 'flyway', now()),
    ('Nissan',        '#c3002f', 'Japon',    120, 'flyway', now()),
    ('Sinotruk Howo', '#d81e05', 'Chine',    130, 'flyway', now()),
    ('Shacman',       '#0a5c36', 'Chine',    140, 'flyway', now()),
    ('Dongfeng',      '#005baa', 'Chine',    150, 'flyway', now()),
    ('Foton',         '#0057a6', 'Chine',    160, 'flyway', now()),
    ('Tata',          '#486aae', 'Inde',     170, 'flyway', now()),
    ('Ashok Leyland', '#e2001a', 'Inde',     180, 'flyway', now()),
    ('Mack',          '#c8102e', 'Etats-Unis', 190, 'flyway', now()),
    ('Freightliner',  '#0033a0', 'Etats-Unis', 200, 'flyway', now()),
    ('Autre',         '#6b7684', NULL,       999, 'flyway', now());

-- Rattache les camions existants a la marque correspondante du referentiel.
UPDATE camion c
SET marque_id = m.id
FROM marque_camion m
WHERE lower(c.marque) = lower(m.nom);
