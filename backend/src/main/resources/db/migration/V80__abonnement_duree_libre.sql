-- Abonnement saisi par le super-administrateur (especes remises en main propre) : un nombre
-- de mois libre, de 1 a 36. Nul : la duree de la periodicite (1, 3, 6 ou 12 mois).
ALTER TABLE abo_paiement ADD COLUMN mois INTEGER;
ALTER TABLE abo_paiement ADD CONSTRAINT ck_abo_paiement_mois CHECK (mois IS NULL OR (mois BETWEEN 1 AND 36));
