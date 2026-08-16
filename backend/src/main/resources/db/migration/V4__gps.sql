-- =====================================================================
-- PARC AUTO - V4 : priorite 4 (geolocalisation)
-- Chapitres 22 a 26 du cahier des charges.
--
-- Le schema est volontairement independant de tout fabricant de traceur :
-- les positions arrivent normalisees par la couche d'integration GPS.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Positions transmises par les traceurs (chapitre 22)
-- ---------------------------------------------------------------------
CREATE TABLE position_gps (
    id             BIGSERIAL PRIMARY KEY,
    camion_id      BIGINT NOT NULL REFERENCES camion (id),
    latitude       NUMERIC(10, 7) NOT NULL,
    longitude      NUMERIC(10, 7) NOT NULL,
    vitesse_kmh    NUMERIC(6, 2),
    direction      INTEGER,
    altitude       NUMERIC(8, 2),
    kilometrage    BIGINT,
    moteur_allume  BOOLEAN,
    horodatage     TIMESTAMPTZ NOT NULL,
    fournisseur    VARCHAR(60),
    device_id      VARCHAR(80),
    cree_le        TIMESTAMPTZ,
    CONSTRAINT position_latitude_valide CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT position_longitude_valide CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT position_vitesse_positive CHECK (vitesse_kmh IS NULL OR vitesse_kmh >= 0)
);

-- La carte et l'historique lisent toujours par camion et par date decroissante.
CREATE INDEX idx_position_camion_horodatage ON position_gps (camion_id, horodatage DESC);
CREATE INDEX idx_position_horodatage ON position_gps (horodatage DESC);

-- ---------------------------------------------------------------------
-- Zones geographiques (chapitre 25)
-- Zones circulaires : centre et rayon, suffisant pour un siege, un depot,
-- une agence ou une zone d'exploitation.
-- ---------------------------------------------------------------------
CREATE TABLE zone_geofence (
    id                  BIGSERIAL PRIMARY KEY,
    nom                 VARCHAR(120) NOT NULL,
    type                VARCHAR(30) NOT NULL,
    latitude            NUMERIC(10, 7) NOT NULL,
    longitude           NUMERIC(10, 7) NOT NULL,
    rayon_metres        INTEGER NOT NULL,
    alerte_entree       BOOLEAN NOT NULL DEFAULT FALSE,
    alerte_sortie       BOOLEAN NOT NULL DEFAULT FALSE,
    duree_max_minutes   INTEGER,
    couleur             VARCHAR(20),
    observations        TEXT,
    actif               BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT zone_rayon_positif CHECK (rayon_metres > 0),
    CONSTRAINT zone_latitude_valide CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT zone_longitude_valide CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_zone_actif ON zone_geofence (actif);

-- ---------------------------------------------------------------------
-- Alertes GPS (chapitre 26)
-- ---------------------------------------------------------------------
CREATE TABLE alerte_gps (
    id           BIGSERIAL PRIMARY KEY,
    camion_id    BIGINT NOT NULL REFERENCES camion (id),
    zone_id      BIGINT REFERENCES zone_geofence (id),
    type         VARCHAR(30) NOT NULL,
    gravite      VARCHAR(20) NOT NULL DEFAULT 'AVERTISSEMENT',
    message      VARCHAR(500) NOT NULL,
    latitude     NUMERIC(10, 7),
    longitude    NUMERIC(10, 7),
    vitesse_kmh  NUMERIC(6, 2),
    horodatage   TIMESTAMPTZ NOT NULL,
    traitee      BOOLEAN NOT NULL DEFAULT FALSE,
    traitee_par  VARCHAR(100),
    traitee_le   TIMESTAMPTZ,
    cree_le      TIMESTAMPTZ,
    cree_par     VARCHAR(100),
    modifie_le   TIMESTAMPTZ,
    modifie_par  VARCHAR(100),
    version      INTEGER
);

CREATE INDEX idx_alerte_camion ON alerte_gps (camion_id, horodatage DESC);
CREATE INDEX idx_alerte_traitee ON alerte_gps (traitee, horodatage DESC);

-- Seuil de vitesse du parc, exploite par le module d'alertes.
ALTER TABLE camion ADD COLUMN vitesse_max_kmh INTEGER;
