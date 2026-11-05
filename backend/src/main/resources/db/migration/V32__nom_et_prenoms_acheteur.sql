-- =============================================================================
-- V32 : nom et prenoms de l'acheteur en ligne saisis separement.
--
-- La boutique demandait le nom complet dans un seul champ, que la fiche client
-- devait ensuite decouper (mal, pour les noms composes). Le nom de famille et
-- les prenoms sont desormais conserves a part ; client_nom garde le nom affiche
-- « Prenoms NOM ». Les commandes et demandes anterieures restent sans detail.
-- =============================================================================

ALTER TABLE mkt_commande
    ADD COLUMN client_nom_famille VARCHAR(100),
    ADD COLUMN client_prenoms     VARCHAR(100);

ALTER TABLE mkt_demande_location
    ADD COLUMN client_nom_famille VARCHAR(100),
    ADD COLUMN client_prenoms     VARCHAR(100);
