-- =====================================================================
-- PARC AUTO - V23 : voitures, utilitaires, camions et engins
--
-- La plateforme accueille aussi bien des parcs de voitures que de poids
-- lourds. Chaque type de vehicule porte une categorie et le dessin affiche
-- sur la carte du vehicule ; chaque marque porte les categories ou elle
-- fabrique, pour filtrer les listes de saisie, et son logo (fichiers servis
-- par l'interface sous /logos).
-- =====================================================================

ALTER TABLE type_camion ADD COLUMN categorie VARCHAR(20);
ALTER TABLE type_camion ADD COLUMN dessin VARCHAR(30);
ALTER TABLE marque_camion ADD COLUMN categories VARCHAR(100);

-- ---------------------------------------------------------------------
-- Types : les types de camions existants gardent leur nom
-- ---------------------------------------------------------------------
UPDATE type_camion t SET categorie = v.categorie, dessin = v.dessin, ordre = v.ordre
FROM (VALUES
    ('Plateau',          'CAMION', 'plateau',         310),
    ('Benne',            'CAMION', 'benne',           320),
    ('Citerne',          'CAMION', 'citerne',         330),
    ('Porte-conteneur',  'CAMION', 'porte-conteneur', 340),
    ('Fourgon',          'CAMION', 'fourgon',         350),
    ('Frigorifique',     'CAMION', 'frigorifique',    360),
    ('Betaillere',       'CAMION', 'betaillere',      370),
    ('Malaxeur',         'CAMION', 'malaxeur',        380),
    ('Porte-engins',     'CAMION', 'porte-engins',    390),
    ('Grue',             'CAMION', 'grue',            400),
    ('Tracteur routier', 'CAMION', 'tracteur',        410),
    ('Remorque',         'CAMION', 'remorque',        420),
    ('Autre',            NULL,     'autre',           999)
) AS v (nom, categorie, dessin, ordre)
WHERE t.nom = v.nom;

INSERT INTO type_camion (nom, description, categorie, dessin, ordre, cree_par, cree_le) VALUES
    ('Citadine',          'Petite voiture de ville',                         'VOITURE',    'citadine',     10, 'flyway', now()),
    ('Berline',           'Voiture a trois volumes',                         'VOITURE',    'berline',      20, 'flyway', now()),
    ('Break',             'Berline allongee au grand coffre',                'VOITURE',    'break',        30, 'flyway', now()),
    ('Coupe',             'Voiture sportive a deux portes',                  'VOITURE',    'coupe',        40, 'flyway', now()),
    ('Cabriolet',         'Voiture decapotable',                             'VOITURE',    'cabriolet',    50, 'flyway', now()),
    ('SUV',               'Vehicule de loisir sureleve',                     'VOITURE',    'suv',          60, 'flyway', now()),
    ('4x4',               'Tout-terrain a quatre roues motrices',            'VOITURE',    '4x4',          70, 'flyway', now()),
    ('Pick-up',           'Cabine et plateau ouvert',                        'VOITURE',    'pickup',       80, 'flyway', now()),
    ('Monospace',         'Voiture familiale a grand volume',                'VOITURE',    'monospace',    90, 'flyway', now()),
    ('Fourgonnette',      'Petit utilitaire de livraison',                   'UTILITAIRE', 'fourgonnette', 110, 'flyway', now()),
    ('Fourgon utilitaire', 'Utilitaire leger a grand volume',                'UTILITAIRE', 'fourgon-leger', 120, 'flyway', now()),
    ('Minibus',           'Transport de 9 a 30 personnes',                   'UTILITAIRE', 'minibus',      130, 'flyway', now()),
    ('Bus',               'Transport urbain de voyageurs',                   'UTILITAIRE', 'bus',          140, 'flyway', now()),
    ('Autocar',           'Transport de voyageurs sur longue distance',      'UTILITAIRE', 'autocar',      150, 'flyway', now()),
    ('Depanneuse',        'Remorquage et transport de vehicules',            'CAMION',     'depanneuse',   405, 'flyway', now()),
    ('Pelleteuse',        'Terrassement',                                    'ENGIN',      'pelleteuse',   510, 'flyway', now()),
    ('Chargeuse',         'Chargement de materiaux',                         'ENGIN',      'chargeuse',    520, 'flyway', now()),
    ('Bulldozer',         'Nivellement et poussee',                          'ENGIN',      'bulldozer',    530, 'flyway', now()),
    ('Chariot elevateur', 'Manutention de charges',                          'ENGIN',      'chariot',      540, 'flyway', now());

-- ---------------------------------------------------------------------
-- Marques : mise a jour des marques existantes, ajout des autres
-- ---------------------------------------------------------------------
CREATE TEMPORARY TABLE marque_import (
    nom VARCHAR(80), logo_url VARCHAR(500), couleur VARCHAR(20), pays VARCHAR(80), categories VARCHAR(100), ordre INTEGER
);

INSERT INTO marque_import (nom, logo_url, couleur, pays, categories, ordre) VALUES
    ('Mercedes-Benz', '/logos/mercedes-benz.png', '#00adef', 'Allemagne', 'VOITURE,UTILITAIRE,CAMION', 10),
    ('Volvo', '/logos/volvo.png', '#003057', 'Suede', 'VOITURE,CAMION,ENGIN', 20),
    ('Scania', '/logos/scania.png', '#041e42', 'Suede', 'CAMION', 30),
    ('MAN', '/logos/man.png', '#e2001a', 'Allemagne', 'UTILITAIRE,CAMION', 40),
    ('Renault Trucks', '/logos/renault.png', '#e30613', 'France', 'CAMION', 50),
    ('Iveco', '/logos/iveco.png', '#004a8f', 'Italie', 'UTILITAIRE,CAMION', 60),
    ('DAF', '/logos/daf.png', '#009fe3', 'Pays-Bas', 'CAMION', 70),
    ('Isuzu', '/logos/isuzu.png', '#c8102e', 'Japon', 'VOITURE,UTILITAIRE,CAMION', 80),
    ('Hino', '/logos/hino.png', '#e60012', 'Japon', 'UTILITAIRE,CAMION', 90),
    ('Mitsubishi Fuso', '/logos/mitsubishi.png', '#e60012', 'Japon', 'UTILITAIRE,CAMION', 100),
    ('Toyota', '/logos/toyota.png', '#eb0a1e', 'Japon', 'VOITURE,UTILITAIRE,CAMION', 110),
    ('Nissan', '/logos/nissan.png', '#c3002f', 'Japon', 'VOITURE,UTILITAIRE,CAMION', 120),
    ('Sinotruk Howo', '/logos/sinotruk.png', '#d81e05', 'Chine', 'CAMION', 130),
    ('Shacman', '/logos/shacman.png', '#0a5c36', 'Chine', 'CAMION', 140),
    ('Dongfeng', '/logos/dongfeng.png', '#005baa', 'Chine', 'VOITURE,UTILITAIRE,CAMION', 150),
    ('Foton', '/logos/foton.png', '#0057a6', 'Chine', 'UTILITAIRE,CAMION', 160),
    ('Tata', '/logos/tata.png', '#486aae', 'Inde', 'VOITURE,UTILITAIRE,CAMION', 170),
    ('Ashok Leyland', NULL, '#e2001a', 'Inde', 'UTILITAIRE,CAMION', 180),
    ('Mack', '/logos/mack.png', '#c8102e', 'Etats-Unis', 'CAMION', 190),
    ('Freightliner', '/logos/freightliner.png', '#0033a0', 'Etats-Unis', 'CAMION', 200),
    ('Peugeot', '/logos/peugeot.png', '#1e3c78', 'France', 'VOITURE,UTILITAIRE', 210),
    ('Renault', '/logos/renault.png', '#efdf00', 'France', 'VOITURE,UTILITAIRE', 220),
    ('Citroen', '/logos/citroen.png', '#a6192e', 'France', 'VOITURE,UTILITAIRE', 230),
    ('DS', '/logos/ds.png', '#2b2b2b', 'France', 'VOITURE', 240),
    ('Dacia', '/logos/dacia.png', '#646b52', 'Roumanie', 'VOITURE,UTILITAIRE', 250),
    ('Volkswagen', '/logos/volkswagen.png', '#001e50', 'Allemagne', 'VOITURE,UTILITAIRE', 260),
    ('Audi', '/logos/audi.png', '#bb0a30', 'Allemagne', 'VOITURE', 270),
    ('BMW', '/logos/bmw.png', '#0066b1', 'Allemagne', 'VOITURE', 280),
    ('Opel', '/logos/opel.png', '#f7ff14', 'Allemagne', 'VOITURE,UTILITAIRE', 290),
    ('Porsche', '/logos/porsche.png', '#b12b28', 'Allemagne', 'VOITURE', 300),
    ('Ford', '/logos/ford.png', '#003478', 'Etats-Unis', 'VOITURE,UTILITAIRE,CAMION', 310),
    ('Chevrolet', '/logos/chevrolet.png', '#d1a33c', 'Etats-Unis', 'VOITURE,UTILITAIRE', 320),
    ('Jeep', '/logos/jeep.png', '#3f4a2c', 'Etats-Unis', 'VOITURE', 330),
    ('Dodge', '/logos/dodge.png', '#c8102e', 'Etats-Unis', 'VOITURE', 340),
    ('RAM', '/logos/ram.png', '#1d1d1b', 'Etats-Unis', 'UTILITAIRE', 350),
    ('GMC', '/logos/gmc.png', '#c8102e', 'Etats-Unis', 'VOITURE,UTILITAIRE', 360),
    ('Cadillac', '/logos/cadillac.png', '#1d1d1b', 'Etats-Unis', 'VOITURE', 370),
    ('Hummer', '/logos/hummer.png', '#1d1d1b', 'Etats-Unis', 'VOITURE', 380),
    ('Tesla', '/logos/tesla.png', '#cc0000', 'Etats-Unis', 'VOITURE', 390),
    ('Lexus', '/logos/lexus.png', '#1a1a1a', 'Japon', 'VOITURE', 400),
    ('Honda', '/logos/honda.png', '#cc0000', 'Japon', 'VOITURE', 410),
    ('Mazda', '/logos/mazda.png', '#101010', 'Japon', 'VOITURE,UTILITAIRE', 420),
    ('Mitsubishi', '/logos/mitsubishi.png', '#e60012', 'Japon', 'VOITURE,UTILITAIRE', 430),
    ('Suzuki', '/logos/suzuki.png', '#e30613', 'Japon', 'VOITURE,UTILITAIRE', 440),
    ('Subaru', '/logos/subaru.png', '#013c74', 'Japon', 'VOITURE', 450),
    ('Daihatsu', '/logos/daihatsu.png', '#e60012', 'Japon', 'VOITURE,UTILITAIRE', 460),
    ('Infiniti', '/logos/infiniti.png', '#1a1a1a', 'Japon', 'VOITURE', 470),
    ('Datsun', '/logos/datsun.png', '#00539b', 'Japon', 'VOITURE', 480),
    ('Hyundai', '/logos/hyundai.png', '#002c5f', 'Coree du Sud', 'VOITURE,UTILITAIRE,CAMION', 490),
    ('Kia', '/logos/kia.png', '#05141f', 'Coree du Sud', 'VOITURE,UTILITAIRE', 500),
    ('SsangYong', '/logos/ssangyong.png', '#003a70', 'Coree du Sud', 'VOITURE,UTILITAIRE', 510),
    ('Genesis', '/logos/genesis.png', '#1a1a1a', 'Coree du Sud', 'VOITURE', 520),
    ('Daewoo', '/logos/daewoo.png', '#0055a5', 'Coree du Sud', 'VOITURE,CAMION', 530),
    ('Fiat', '/logos/fiat.png', '#9a1b30', 'Italie', 'VOITURE,UTILITAIRE', 540),
    ('Alfa Romeo', '/logos/alfa-romeo.png', '#981e32', 'Italie', 'VOITURE', 550),
    ('Lancia', '/logos/lancia.png', '#00387b', 'Italie', 'VOITURE', 560),
    ('Ferrari', '/logos/ferrari.png', '#ffcc00', 'Italie', 'VOITURE', 570),
    ('Lamborghini', '/logos/lamborghini.png', '#ddb321', 'Italie', 'VOITURE', 580),
    ('Maserati', '/logos/maserati.png', '#0c2340', 'Italie', 'VOITURE', 590),
    ('Land Rover', '/logos/land-rover.png', '#005a2b', 'Royaume-Uni', 'VOITURE', 600),
    ('Jaguar', '/logos/jaguar.png', '#1a1a1a', 'Royaume-Uni', 'VOITURE', 610),
    ('Mini', '/logos/mini.png', '#1a1a1a', 'Royaume-Uni', 'VOITURE', 620),
    ('Bentley', '/logos/bentley.png', '#333333', 'Royaume-Uni', 'VOITURE', 630),
    ('Rolls-Royce', '/logos/rolls-royce.png', '#1a1a1a', 'Royaume-Uni', 'VOITURE', 640),
    ('Aston Martin', '/logos/aston-martin.png', '#00665e', 'Royaume-Uni', 'VOITURE', 650),
    ('MG', '/logos/mg.png', '#a6192e', 'Chine', 'VOITURE', 660),
    ('SEAT', '/logos/seat.png', '#e2231a', 'Espagne', 'VOITURE', 670),
    ('Cupra', '/logos/cupra.png', '#95572b', 'Espagne', 'VOITURE', 680),
    ('Skoda', '/logos/skoda.png', '#4ba82e', 'Republique tcheque', 'VOITURE', 690),
    ('Smart', '/logos/smart.png', '#1a1a1a', 'Allemagne', 'VOITURE', 700),
    ('Lada', '/logos/lada.png', '#0b3b8c', 'Russie', 'VOITURE', 710),
    ('UAZ', '/logos/uaz.png', '#1a1a1a', 'Russie', 'VOITURE,UTILITAIRE', 720),
    ('Proton', '/logos/proton.png', '#1a1a1a', 'Malaisie', 'VOITURE', 730),
    ('Perodua', '/logos/perodua.png', '#00843d', 'Malaisie', 'VOITURE', 740),
    ('Mahindra', '/logos/mahindra.png', '#c8102e', 'Inde', 'VOITURE,UTILITAIRE', 750),
    ('BYD', '/logos/byd.png', '#c8102e', 'Chine', 'VOITURE,UTILITAIRE', 760),
    ('Chery', '/logos/chery.png', '#c8102e', 'Chine', 'VOITURE', 770),
    ('Geely', '/logos/geely.png', '#1f4e9c', 'Chine', 'VOITURE', 780),
    ('Haval', '/logos/haval.png', '#1a1a1a', 'Chine', 'VOITURE', 790),
    ('Great Wall', '/logos/great-wall.png', '#c8102e', 'Chine', 'VOITURE,UTILITAIRE', 800),
    ('Changan', '/logos/changan.png', '#0050a0', 'Chine', 'VOITURE,UTILITAIRE', 810),
    ('JAC', '/logos/jac.png', '#c8102e', 'Chine', 'VOITURE,UTILITAIRE,CAMION', 820),
    ('JMC', '/logos/jmc.png', '#0050a0', 'Chine', 'UTILITAIRE,CAMION', 830),
    ('BAIC', '/logos/baic-motor.png', '#c8102e', 'Chine', 'VOITURE,UTILITAIRE', 840),
    ('Brilliance', '/logos/brilliance.png', '#1a1a1a', 'Chine', 'VOITURE,UTILITAIRE', 850),
    ('Jetour', '/logos/jetour.png', '#1a1a1a', 'Chine', 'VOITURE', 860),
    ('Omoda', '/logos/omoda.png', '#1a1a1a', 'Chine', 'VOITURE', 870),
    ('Exeed', '/logos/exeed.png', '#1a1a1a', 'Chine', 'VOITURE', 880),
    ('Lifan', '/logos/lifan.png', '#0050a0', 'Chine', 'VOITURE', 890),
    ('Wuling', '/logos/wuling.png', '#c8102e', 'Chine', 'VOITURE,UTILITAIRE', 900),
    ('Maxus', '/logos/maxus.png', '#c8102e', 'Chine', 'UTILITAIRE', 910),
    ('Hongqi', '/logos/hongqi.png', '#c8102e', 'Chine', 'VOITURE', 920),
    ('GAC', '/logos/gac-group.png', '#1a1a1a', 'Chine', 'VOITURE', 930),
    ('Zotye', '/logos/zotye.png', '#c8102e', 'Chine', 'VOITURE', 940),
    ('Soueast', '/logos/soueast.png', '#0050a0', 'Chine', 'VOITURE', 950),
    ('Haima', '/logos/haima.png', '#c8102e', 'Chine', 'VOITURE', 960),
    ('Gonow', '/logos/gonow.png', '#0050a0', 'Chine', 'VOITURE,UTILITAIRE', 970),
    ('Landwind', '/logos/landwind.png', '#1a1a1a', 'Chine', 'VOITURE', 980),
    ('Zeekr', '/logos/zeekr.png', '#1a1a1a', 'Chine', 'VOITURE', 990),
    ('NIO', '/logos/nio.png', '#1a1a1a', 'Chine', 'VOITURE', 1000),
    ('XPeng', '/logos/xpeng.png', '#1a1a1a', 'Chine', 'VOITURE', 1010),
    ('Polestar', '/logos/polestar.png', '#1a1a1a', 'Suede', 'VOITURE', 1020),
    ('IKCO', '/logos/ikco.png', '#0050a0', 'Iran', 'VOITURE', 1030),
    ('Saipa', '/logos/saipa.png', '#0050a0', 'Iran', 'VOITURE', 1040),
    ('VinFast', '/logos/vinfast.png', '#1a1a1a', 'Vietnam', 'VOITURE', 1050),
    ('FAW', '/logos/faw.png', '#0050a0', 'Chine', 'VOITURE,UTILITAIRE,CAMION', 1060),
    ('FAW Jiefang', '/logos/faw-jiefang.png', '#0050a0', 'Chine', 'CAMION', 1070),
    ('Beiben', '/logos/beiben.png', '#c8102e', 'Chine', 'CAMION', 1080),
    ('Hongyan', '/logos/hongyan.png', '#c8102e', 'Chine', 'CAMION', 1090),
    ('CAMC', '/logos/camc.png', '#0050a0', 'Chine', 'CAMION', 1100),
    ('Dayun', '/logos/dayun.png', '#0050a0', 'Chine', 'CAMION', 1110),
    ('Yutong', '/logos/yutong.png', '#c8102e', 'Chine', 'UTILITAIRE', 1120),
    ('King Long', '/logos/king-long.png', '#0050a0', 'Chine', 'UTILITAIRE', 1130),
    ('Higer', '/logos/higer.png', '#0050a0', 'Chine', 'UTILITAIRE', 1140),
    ('Golden Dragon', '/logos/golden-dragon.png', '#c8102e', 'Chine', 'UTILITAIRE', 1150),
    ('Zhongtong', '/logos/zhongtong.png', '#0050a0', 'Chine', 'UTILITAIRE', 1160),
    ('Kamaz', '/logos/kamaz.png', '#0050a0', 'Russie', 'CAMION', 1170),
    ('MAZ', '/logos/maz.png', '#0050a0', 'Bielorussie', 'CAMION', 1180),
    ('GAZ', '/logos/gaz.png', '#0050a0', 'Russie', 'UTILITAIRE,CAMION', 1190),
    ('Tatra', '/logos/tatra.png', '#c8102e', 'Republique tcheque', 'CAMION', 1200),
    ('BharatBenz', '/logos/bharatbenz.png', '#1a1a1a', 'Inde', 'CAMION', 1210),
    ('Eicher', '/logos/eicher.png', '#c8102e', 'Inde', 'UTILITAIRE,CAMION', 1220),
    ('Force Motors', '/logos/force-motors.png', '#c8102e', 'Inde', 'UTILITAIRE', 1230),
    ('UD Trucks', '/logos/ud.png', '#c8102e', 'Japon', 'CAMION', 1240),
    ('Kenworth', '/logos/kenworth.png', '#c8102e', 'Etats-Unis', 'CAMION', 1250),
    ('Peterbilt', '/logos/peterbilt.png', '#c8102e', 'Etats-Unis', 'CAMION', 1260),
    ('International', '/logos/international.png', '#c8102e', 'Etats-Unis', 'CAMION', 1270),
    ('Western Star', '/logos/western-star.png', '#1a1a1a', 'Etats-Unis', 'CAMION', 1280),
    ('Setra', '/logos/setra.png', '#1a1a1a', 'Allemagne', 'UTILITAIRE', 1290),
    ('Irizar', '/logos/irizar.png', '#1a1a1a', 'Espagne', 'UTILITAIRE', 1300),
    ('Sisu', '/logos/sisu.png', '#0050a0', 'Finlande', 'CAMION', 1310),
    ('Caterpillar', NULL, '#ffcd11', 'Etats-Unis', 'ENGIN', 1320),
    ('Komatsu', NULL, '#1f3f8c', 'Japon', 'ENGIN', 1330),
    ('JCB', NULL, '#fcb316', 'Royaume-Uni', 'ENGIN', 1340),
    ('Liebherr', NULL, '#ffd200', 'Allemagne', 'ENGIN', 1350),
    ('Sany', NULL, '#d7000f', 'Chine', 'ENGIN,CAMION', 1360),
    ('XCMG', NULL, '#f5a800', 'Chine', 'ENGIN,CAMION', 1370),
    ('Doosan', NULL, '#0067b1', 'Coree du Sud', 'ENGIN', 1380),
    ('Hitachi', NULL, '#e60012', 'Japon', 'ENGIN', 1390),
    ('Case', NULL, '#c8102e', 'Etats-Unis', 'ENGIN', 1400),
    ('John Deere', NULL, '#367c2b', 'Etats-Unis', 'ENGIN', 1410),
    ('Manitou', NULL, '#e2001a', 'France', 'ENGIN', 1420),
    ('Bobcat', NULL, '#f26722', 'Etats-Unis', 'ENGIN', 1430);

UPDATE marque_camion m
SET logo_url = i.logo_url, couleur = i.couleur, pays = i.pays, categories = i.categories, ordre = i.ordre,
    modifie_par = 'flyway', modifie_le = now()
FROM marque_import i
WHERE lower(m.nom) = lower(i.nom);

INSERT INTO marque_camion (nom, logo_url, couleur, pays, categories, ordre, cree_par, cree_le)
SELECT i.nom, i.logo_url, i.couleur, i.pays, i.categories, i.ordre, 'flyway', now()
FROM marque_import i
WHERE NOT EXISTS (SELECT 1 FROM marque_camion m WHERE lower(m.nom) = lower(i.nom));

-- « Autre » reste en fin de liste, dans toutes les categories.
UPDATE marque_camion SET ordre = 9999 WHERE nom = 'Autre';

DROP TABLE marque_import;
