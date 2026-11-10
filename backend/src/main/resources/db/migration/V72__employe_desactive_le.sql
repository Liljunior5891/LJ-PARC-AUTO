-- Jour de desactivation d'un employe : a partir de ce jour, il n'est plus
-- attendu (ni absent) dans les historiques et les taux de ponctualite.
ALTER TABLE pres_employe ADD COLUMN desactive_le DATE;

UPDATE pres_employe e
SET desactive_le = COALESCE((SELECT max(p.jour) + 1 FROM pres_pointage p WHERE p.employe_id = e.id), CURRENT_DATE)
WHERE NOT e.actif;
