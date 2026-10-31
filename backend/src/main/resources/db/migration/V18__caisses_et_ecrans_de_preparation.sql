-- =============================================================================
-- V18 : caisses et ecrans de preparation crees par le gerant.
--
-- Une caisse est un poste d'encaissement nomme (« Caisse bar », « Caisse
-- terrasse »), affecte a des utilisateurs ; chaque session de caisse se tient
-- sur l'une d'elles, et une caisse n'est ouverte que par une personne a la
-- fois. Un ecran de preparation recoit les articles de categories choisies et
-- n'est ouvert qu'a ses utilisateurs.
--
-- Reprise : une « Caisse principale » par entreprise recoit l'historique des
-- sessions et ses caissiers et serveurs ; les ecrans « Cuisine » et « Bar »
-- sont recrees a partir des postes des categories, avec les cuisiniers.
-- =============================================================================

CREATE TABLE pdv_caisse (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    nom           VARCHAR(80) NOT NULL,
    description   VARCHAR(255),
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_caisse_nom UNIQUE (entreprise_id, nom)
);

CREATE TABLE pdv_caisse_utilisateur (
    caisse_id      BIGINT NOT NULL REFERENCES pdv_caisse (id) ON DELETE CASCADE,
    utilisateur_id BIGINT NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    PRIMARY KEY (caisse_id, utilisateur_id)
);

-- Jusqu'ici chaque personne tenait sa propre caisse, et plusieurs pouvaient
-- etre ouvertes en meme temps : chacune devient une caisse a son nom, avec son
-- historique. Une entreprise sans historique recoit une « Caisse principale ».
INSERT INTO pdv_caisse (entreprise_id, nom, actif, cree_le, cree_par, version)
SELECT DISTINCT ON (entreprise_id, caissier_nom) entreprise_id, left('Caisse ' || caissier_nom, 80), TRUE, now(), 'migration', 0
FROM pdv_session_caisse
ORDER BY entreprise_id, caissier_nom;

INSERT INTO pdv_caisse (entreprise_id, nom, actif, cree_le, cree_par, version)
SELECT m.entreprise_id, 'Caisse principale', TRUE, now(), 'migration', 0
FROM entreprise_module m
WHERE m.module = 'POINT_DE_VENTE'
  AND NOT EXISTS (SELECT 1 FROM pdv_caisse c WHERE c.entreprise_id = m.entreprise_id);

ALTER TABLE pdv_session_caisse ADD COLUMN caisse_id BIGINT REFERENCES pdv_caisse (id);
UPDATE pdv_session_caisse s SET caisse_id = c.id
FROM pdv_caisse c
WHERE c.entreprise_id = s.entreprise_id AND c.nom = left('Caisse ' || s.caissier_nom, 80);

-- Chacun garde sa caisse ; le personnel de caisse qui n'en tenait pas encore
-- peut ouvrir toutes celles de son entreprise, jusqu'a ce que le gerant les repartisse.
INSERT INTO pdv_caisse_utilisateur (caisse_id, utilisateur_id)
SELECT DISTINCT s.caisse_id, s.caissier_id
FROM pdv_session_caisse s;

INSERT INTO pdv_caisse_utilisateur (caisse_id, utilisateur_id)
SELECT DISTINCT c.id, u.id
FROM pdv_caisse c
JOIN utilisateur u ON u.entreprise_id = c.entreprise_id
JOIN utilisateur_role r ON r.utilisateur_id = u.id
WHERE r.role IN ('CAISSIER', 'SERVEUR')
  AND NOT EXISTS (SELECT 1 FROM pdv_session_caisse s WHERE s.caissier_id = u.id)
ON CONFLICT DO NOTHING;

ALTER TABLE pdv_session_caisse ALTER COLUMN caisse_id SET NOT NULL;
-- Une caisse n'est tenue que par une personne a la fois.
CREATE UNIQUE INDEX uk_pdv_session_caisse_ouverte ON pdv_session_caisse (caisse_id) WHERE statut = 'OUVERTE';
CREATE INDEX idx_pdv_session_caisse ON pdv_session_caisse (caisse_id, ouverte_le DESC);

CREATE TABLE pdv_ecran_preparation (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    nom           VARCHAR(80) NOT NULL,
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_ecran_nom UNIQUE (entreprise_id, nom)
);

CREATE TABLE pdv_ecran_categorie (
    ecran_id     BIGINT NOT NULL REFERENCES pdv_ecran_preparation (id) ON DELETE CASCADE,
    categorie_id BIGINT NOT NULL REFERENCES pdv_categorie (id) ON DELETE CASCADE,
    PRIMARY KEY (ecran_id, categorie_id)
);

CREATE TABLE pdv_ecran_utilisateur (
    ecran_id       BIGINT NOT NULL REFERENCES pdv_ecran_preparation (id) ON DELETE CASCADE,
    utilisateur_id BIGINT NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    PRIMARY KEY (ecran_id, utilisateur_id)
);

INSERT INTO pdv_ecran_preparation (entreprise_id, nom, actif, cree_le, cree_par, version)
SELECT DISTINCT entreprise_id, CASE poste WHEN 'CUISINE' THEN 'Cuisine' ELSE 'Bar' END, TRUE, now(), 'migration', 0
FROM pdv_categorie
WHERE poste IN ('CUISINE', 'BAR');

INSERT INTO pdv_ecran_categorie (ecran_id, categorie_id)
SELECT e.id, c.id
FROM pdv_ecran_preparation e
JOIN pdv_categorie c ON c.entreprise_id = e.entreprise_id
 AND ((e.nom = 'Cuisine' AND c.poste = 'CUISINE') OR (e.nom = 'Bar' AND c.poste = 'BAR'));

INSERT INTO pdv_ecran_utilisateur (ecran_id, utilisateur_id)
SELECT DISTINCT e.id, u.id
FROM pdv_ecran_preparation e
JOIN utilisateur u ON u.entreprise_id = e.entreprise_id
JOIN utilisateur_role r ON r.utilisateur_id = u.id
WHERE r.role = 'PREPARATEUR';
