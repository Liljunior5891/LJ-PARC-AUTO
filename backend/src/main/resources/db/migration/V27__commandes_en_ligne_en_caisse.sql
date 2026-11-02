-- =============================================================================
-- V27 : les commandes en ligne passent par la caisse du point de vente.
--
-- Pour une entreprise qui a le point de vente, une commande passee dans la
-- boutique en ligne est traitee comme une commande sur place : la caisse
-- l'accepte, puis (au restaurant) elle part sur les ecrans de preparation et
-- s'encaisse comme les autres. Les commandes et les ventes en ligne restent
-- identifiees, pour les reconnaitre a l'ecran et separer le chiffre d'affaires
-- en ligne de celui du comptoir.
-- =============================================================================

-- Commande de restaurant nee d'une commande en ligne.
ALTER TABLE pdv_commande ADD COLUMN en_ligne BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pdv_commande ADD COLUMN commande_en_ligne_id BIGINT REFERENCES mkt_commande (id);

-- Vente encaissee pour une commande en ligne.
ALTER TABLE pdv_vente ADD COLUMN en_ligne BOOLEAN NOT NULL DEFAULT FALSE;

-- Commande en ligne confiee a la caisse (entreprise avec point de vente),
-- et, au restaurant, la commande de salle qui la porte.
ALTER TABLE mkt_commande ADD COLUMN en_caisse BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE mkt_commande ADD COLUMN pdv_commande_id BIGINT REFERENCES pdv_commande (id);
CREATE INDEX idx_mkt_commande_caisse ON mkt_commande (entreprise_id, en_caisse, statut);
