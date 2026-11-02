-- =============================================================================
-- V28 : contrats de location nes d'une demande faite dans la boutique en ligne.
--
-- Pour une entreprise qui a la gestion de parc et la Marketplace, un contrat
-- issu d'une demande en ligne est marque comme tel : les listes (contrats,
-- recettes, factures) et le tableau de bord distinguent le canal « en ligne »
-- de celui de l'agence.
-- =============================================================================

ALTER TABLE location ADD COLUMN en_ligne BOOLEAN NOT NULL DEFAULT FALSE;

-- Les contrats deja crees par l'acceptation d'une demande en ligne.
UPDATE location l SET en_ligne = TRUE
FROM mkt_demande_location d
WHERE d.location_id = l.id;
