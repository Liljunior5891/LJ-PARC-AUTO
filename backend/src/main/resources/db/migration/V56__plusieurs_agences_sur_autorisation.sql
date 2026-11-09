-- Plusieurs agences : option accordee par le super-administrateur de la plateforme.
-- Par defaut une entreprise n'a qu'une agence ; celles qui en ont deja plusieurs gardent le droit.
ALTER TABLE entreprise ADD COLUMN IF NOT EXISTS agences_multiples BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE entreprise SET agences_multiples = TRUE
WHERE id IN (SELECT entreprise_id FROM agc_agence GROUP BY entreprise_id HAVING count(*) > 1);
