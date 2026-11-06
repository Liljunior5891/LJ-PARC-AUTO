-- Format des documents imprimes (factures, bons, devis, avoirs...) choisi par
-- l'administrateur : A4 (par defaut) ou A5.
ALTER TABLE parametre_societe ADD COLUMN format_documents VARCHAR(2) NOT NULL DEFAULT 'A4';
ALTER TABLE parametre_societe ADD CONSTRAINT ck_parametre_societe_format CHECK (format_documents IN ('A4', 'A5'));
