-- =============================================================================
-- V31 : photos supplementaires des offres en ligne.
--
-- Un produit du point de vente ou un vehicule vendu / loue en ligne peut porter,
-- en plus de sa photo principale, une galerie de photos (reduites dans le
-- navigateur, enregistrees encodees). Dans la boutique en ligne, un clic sur la
-- photo principale ouvre la galerie.
-- =============================================================================

CREATE TABLE mkt_offre_photo (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    offre_id      BIGINT NOT NULL REFERENCES mkt_offre (id) ON DELETE CASCADE,
    image         TEXT NOT NULL,
    ordre         INTEGER NOT NULL DEFAULT 0,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER
);
CREATE INDEX idx_mkt_offre_photo_offre ON mkt_offre_photo (offre_id, ordre);
