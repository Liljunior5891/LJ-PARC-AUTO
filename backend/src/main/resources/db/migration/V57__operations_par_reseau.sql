-- Operation propre a un reseau (Ria, Kori... sous Rapid Transfer) ; nul : commune a tous les reseaux du service.
ALTER TABLE agc_type_operation ADD COLUMN IF NOT EXISTS reseau_id BIGINT REFERENCES agc_reseau (id);
CREATE INDEX IF NOT EXISTS idx_agc_type_operation_reseau ON agc_type_operation (reseau_id);
