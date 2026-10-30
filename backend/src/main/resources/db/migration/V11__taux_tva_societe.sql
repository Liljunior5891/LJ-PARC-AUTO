-- Le taux de TVA se parametre avec l'entreprise : il ne se saisit plus facture
-- par facture. Les factures deja etablies conservent le taux qui leur a ete
-- applique, colonne facture.taux_tva.
ALTER TABLE parametre_societe
    ADD COLUMN taux_tva NUMERIC(5, 2) NOT NULL DEFAULT 18;
