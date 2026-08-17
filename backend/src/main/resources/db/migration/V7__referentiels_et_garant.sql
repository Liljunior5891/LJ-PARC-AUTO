-- =====================================================================
-- PARC AUTO - V7 : referentiels de saisie et garant du chauffeur
-- =====================================================================

-- ---------------------------------------------------------------------
-- Types de camions (referentiel de saisie)
-- ---------------------------------------------------------------------
CREATE TABLE type_camion (
    id          BIGSERIAL PRIMARY KEY,
    nom         VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(255),
    ordre       INTEGER NOT NULL DEFAULT 100,
    actif       BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le     TIMESTAMPTZ,
    cree_par    VARCHAR(100),
    modifie_le  TIMESTAMPTZ,
    modifie_par VARCHAR(100),
    version     INTEGER
);

INSERT INTO type_camion (nom, description, ordre, cree_par, cree_le) VALUES
    ('Plateau',          'Transport de charges non palettisees',        10, 'flyway', now()),
    ('Benne',            'Materiaux en vrac : sable, gravier, terre',   20, 'flyway', now()),
    ('Citerne',          'Liquides : carburant, eau, produits chimiques', 30, 'flyway', now()),
    ('Porte-conteneur',  'Conteneurs 20 et 40 pieds',                   40, 'flyway', now()),
    ('Fourgon',          'Marchandises abritees',                       50, 'flyway', now()),
    ('Frigorifique',     'Denrees sous temperature dirigee',            60, 'flyway', now()),
    ('Betaillere',       'Transport d''animaux',                        70, 'flyway', now()),
    ('Malaxeur',         'Beton pret a l''emploi',                      80, 'flyway', now()),
    ('Porte-engins',     'Engins de chantier',                          90, 'flyway', now()),
    ('Grue',             'Levage',                                     100, 'flyway', now()),
    ('Tracteur routier', 'Attelage de semi-remorques',                 110, 'flyway', now()),
    ('Remorque',         'Remorque seule',                             120, 'flyway', now()),
    ('Autre',            NULL,                                         999, 'flyway', now());

-- ---------------------------------------------------------------------
-- Couleurs (referentiel de saisie)
-- ---------------------------------------------------------------------
CREATE TABLE couleur_camion (
    id          BIGSERIAL PRIMARY KEY,
    nom         VARCHAR(60) NOT NULL UNIQUE,
    code_hexa   VARCHAR(20),
    ordre       INTEGER NOT NULL DEFAULT 100,
    actif       BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le     TIMESTAMPTZ,
    cree_par    VARCHAR(100),
    modifie_le  TIMESTAMPTZ,
    modifie_par VARCHAR(100),
    version     INTEGER
);

INSERT INTO couleur_camion (nom, code_hexa, ordre, cree_par, cree_le) VALUES
    ('Blanc',      '#ffffff',  10, 'flyway', now()),
    ('Gris',       '#9aa3ad',  20, 'flyway', now()),
    ('Noir',       '#1f2733',  30, 'flyway', now()),
    ('Rouge',      '#c62828',  40, 'flyway', now()),
    ('Bleu',       '#1565c0',  50, 'flyway', now()),
    ('Vert',       '#1e874b',  60, 'flyway', now()),
    ('Jaune',      '#f0c202',  70, 'flyway', now()),
    ('Orange',     '#e07b00',  80, 'flyway', now()),
    ('Marron',     '#7a4b25',  90, 'flyway', now()),
    ('Beige',      '#d8cbb3', 100, 'flyway', now()),
    ('Bicolore',   NULL,      110, 'flyway', now()),
    ('Autre',      NULL,      999, 'flyway', now());

ALTER TABLE camion ADD COLUMN type_id BIGINT REFERENCES type_camion (id);
ALTER TABLE camion ADD COLUMN couleur_id BIGINT REFERENCES couleur_camion (id);

-- Rattache les camions existants aux referentiels.
UPDATE camion c SET type_id = t.id FROM type_camion t WHERE lower(c.type) = lower(t.nom);
UPDATE camion c SET couleur_id = k.id FROM couleur_camion k WHERE lower(c.couleur) = lower(k.nom);

-- ---------------------------------------------------------------------
-- Garant du chauffeur : la personne a prevenir et qui se porte caution
-- ---------------------------------------------------------------------
ALTER TABLE chauffeur ADD COLUMN garant_nom VARCHAR(150);
ALTER TABLE chauffeur ADD COLUMN garant_lien VARCHAR(80);
ALTER TABLE chauffeur ADD COLUMN garant_telephone VARCHAR(30);
ALTER TABLE chauffeur ADD COLUMN garant_telephone_secondaire VARCHAR(30);
ALTER TABLE chauffeur ADD COLUMN garant_adresse VARCHAR(255);
ALTER TABLE chauffeur ADD COLUMN garant_profession VARCHAR(120);
ALTER TABLE chauffeur ADD COLUMN garant_piece_identite VARCHAR(80);

-- Le dossier du garant se range parmi les documents du chauffeur : le type de
-- document distingue les pieces du chauffeur de celles de son garant.
