-- Le suivi de stock d'un article peut etre active ou coupe apres une vente :
-- chaque ligne garde ce qui valait au moment ou le stock a bouge, pour que
-- l'annulation rende exactement ce qui est sorti (et libere ce qui est reserve).

ALTER TABLE pdv_ligne_vente ADD COLUMN suivi_stock BOOLEAN;
UPDATE pdv_ligne_vente l SET suivi_stock = a.suivi_stock FROM pdv_article a WHERE a.id = l.article_id;

-- Restaurant : le stock sort a l'envoi en preparation, depuis le magasin de la caisse.
ALTER TABLE pdv_ligne_commande ADD COLUMN stock_sorti BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pdv_ligne_commande ADD COLUMN lieu_id BIGINT REFERENCES pdv_lieu_stock (id);
UPDATE pdv_ligne_commande l SET stock_sorti = TRUE
FROM pdv_article a
WHERE a.id = l.article_id AND a.suivi_stock AND l.envoyee_le IS NOT NULL;

-- Ticket de caisse qui a regle une commande en ligne : son annulation rouvre la commande.
ALTER TABLE mkt_commande ADD COLUMN vente_id BIGINT;
CREATE INDEX ix_mkt_commande_vente ON mkt_commande (vente_id);
