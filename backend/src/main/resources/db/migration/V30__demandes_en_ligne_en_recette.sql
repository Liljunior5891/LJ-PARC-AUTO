-- =============================================================================
-- V30 : une demande de location en ligne se conclut par une recette en agence.
--
-- Accepter une demande ne cree plus de contrat : elle reserve le vehicule sur
-- ses dates. Le client se presente en agence, ou l'on enregistre la recette
-- (type au choix) et sa fiche client complete ; la demande passe alors
-- « conclue » et la recette porte le canal « en ligne ». Une demande acceptee
-- sans recette a sa date de debut expire et libere le vehicule.
-- =============================================================================

ALTER TABLE mkt_demande_location DROP CONSTRAINT ck_mkt_demande_statut;
ALTER TABLE mkt_demande_location ADD CONSTRAINT ck_mkt_demande_statut
    CHECK (statut IN ('NOUVELLE', 'ACCEPTEE', 'REFUSEE', 'ANNULEE', 'EXPIREE', 'CONCLUE'));
ALTER TABLE mkt_demande_location ADD COLUMN recette_id BIGINT REFERENCES recette (id);

-- Le canal « en ligne » est porte par la recette (et sa facture) elle-meme,
-- quel que soit son type ; les recettes et factures des contrats nes en ligne le recoivent.
ALTER TABLE recette ADD COLUMN en_ligne BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE facture ADD COLUMN en_ligne BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE recette r SET en_ligne = TRUE FROM location l WHERE r.location_id = l.id AND l.en_ligne;
UPDATE facture f SET en_ligne = TRUE FROM location l WHERE f.location_id = l.id AND l.en_ligne;

-- Les demandes deja acceptees avec un contrat sont conclues.
UPDATE mkt_demande_location SET statut = 'CONCLUE' WHERE statut = 'ACCEPTEE' AND location_id IS NOT NULL;
