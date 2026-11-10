-- Paiement en ligne relance : les references precedentes restent reconnues,
-- la confirmation tardive d'une ancienne transaction n'est plus perdue.
ALTER TABLE mkt_commande ADD COLUMN references_precedentes VARCHAR(1000);

-- Paye en ligne (mobile money confirme par le fournisseur) : l'annulation du
-- ticket de caisse ne doit pas faire repayer le client.
ALTER TABLE mkt_commande ADD COLUMN payee_en_ligne BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mkt_commande SET payee_en_ligne = TRUE
WHERE mode_paiement = 'MOBILE_MONEY' AND statut_paiement = 'PAYEE' AND vente_id IS NULL;
