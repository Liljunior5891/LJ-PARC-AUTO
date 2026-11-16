-- Petite caisse de depenses des agences : un compte cree par l'administrateur,
-- affecte a un caissier et alimente par un mouvement ; le caissier ne paie ses
-- depenses que depuis elle.
ALTER TABLE agc_compte DROP CONSTRAINT ck_agc_compte_type;
ALTER TABLE agc_compte ADD CONSTRAINT ck_agc_compte_type CHECK (type IN ('CAISSE_GENERALE', 'CAISSE_CAISSIER', 'PETITE_CAISSE',
    'FLOAT', 'RECHARGE', 'COMMISSIONS', 'BANQUE', 'FONDS_EXPLOITATION'));
ALTER TABLE agc_compte DROP CONSTRAINT ck_agc_compte_caissier;
ALTER TABLE agc_compte ADD CONSTRAINT ck_agc_compte_caissier
    CHECK ((type IN ('CAISSE_CAISSIER', 'PETITE_CAISSE')) = (utilisateur_id IS NOT NULL));
-- Une seule petite caisse active par caissier et par agence.
CREATE UNIQUE INDEX uk_agc_compte_petite_caisse ON agc_compte (agence_id, utilisateur_id)
    WHERE type = 'PETITE_CAISSE' AND actif;
