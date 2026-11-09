-- Logo de chaque service et de chaque reseau (data URL reduite par le navigateur), affiche dans les listes.
ALTER TABLE agc_service ADD COLUMN IF NOT EXISTS logo TEXT;
ALTER TABLE agc_reseau ADD COLUMN IF NOT EXISTS logo TEXT;
