-- Par defaut, la commission est creditee des la transaction : pas de rapprochement a faire.
-- Le rapprochement reste possible service par service (case « versement differe »).
ALTER TABLE agc_service ALTER COLUMN commission_differee SET DEFAULT FALSE;
UPDATE agc_service SET commission_differee = FALSE;
