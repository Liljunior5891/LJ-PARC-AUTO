-- Vente de cartes : prenom du client et numero de chaque carte vendue (une vente peut en compter plusieurs).
ALTER TABLE agc_vente_carte ADD COLUMN IF NOT EXISTS client_prenom VARCHAR(150);

CREATE TABLE IF NOT EXISTS agc_vente_carte_numero (
    vente_id BIGINT NOT NULL REFERENCES agc_vente_carte (id) ON DELETE CASCADE,
    numero   VARCHAR(40) NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_agc_vente_carte_numero_vente ON agc_vente_carte_numero (vente_id);
CREATE INDEX IF NOT EXISTS ix_agc_vente_carte_numero ON agc_vente_carte_numero (numero);
