-- V45 : les reglages et les effets des types d'operation passent en INTEGER,
-- le type que l'application lit (V44 les avait crees en SMALLINT).
ALTER TABLE agc_reglage ALTER COLUMN heure_bascule TYPE INTEGER;
ALTER TABLE agc_type_operation ALTER COLUMN effet_cash TYPE INTEGER;
ALTER TABLE agc_type_operation ALTER COLUMN effet_virtuel TYPE INTEGER;
