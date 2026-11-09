-- Delai (en minutes) sans geste apres lequel l'ecran de caisse se verrouille et
-- demande le code PIN : regle par l'administrateur, pour le point de vente
-- (caissiers et serveurs) et pour les caissiers d'agence. 5 minutes par defaut.
ALTER TABLE pdv_reglage ADD COLUMN minutes_verrou_caisse INTEGER NOT NULL DEFAULT 5;
ALTER TABLE pdv_reglage ADD CONSTRAINT ck_pdv_reglage_verrou CHECK (minutes_verrou_caisse BETWEEN 1 AND 120);

ALTER TABLE agc_reglage ADD COLUMN minutes_verrou_caisse INTEGER NOT NULL DEFAULT 5;
ALTER TABLE agc_reglage ADD CONSTRAINT ck_agc_reglage_verrou CHECK (minutes_verrou_caisse BETWEEN 1 AND 120);
