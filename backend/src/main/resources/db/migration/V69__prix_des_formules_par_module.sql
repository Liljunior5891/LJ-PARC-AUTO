-- Abonnements : chaque formule fixe un prix mensuel par module. L'entreprise paie
-- la somme des modules qu'elle choisit, au prix de sa formule. Le prix unique de
-- la formule, le nombre de modules compris et les prix des modules en option
-- disparaissent ; les limites restent portees par la formule.

CREATE TABLE abo_formule_prix (
    formule_id   BIGINT         NOT NULL REFERENCES abo_formule (id) ON DELETE CASCADE,
    module       VARCHAR(40)    NOT NULL,
    prix_mensuel NUMERIC(14, 2) NOT NULL,
    PRIMARY KEY (formule_id, module),
    CONSTRAINT ck_abo_formule_prix CHECK (prix_mensuel >= 0)
);

-- Prix de depart : le tarif d'option du module, double en Pro et triple en Premium.
INSERT INTO abo_formule_prix (formule_id, module, prix_mensuel)
SELECT f.id, t.module,
       t.prix_mensuel * CASE f.nom WHEN 'Pro' THEN 2 WHEN 'Premium' THEN 3 ELSE 1 END
FROM abo_formule f CROSS JOIN abo_tarif_module t;

ALTER TABLE abo_formule DROP COLUMN prix_mensuel;
ALTER TABLE abo_formule DROP COLUMN modules_inclus;
DROP TABLE abo_tarif_module;

-- Detail facture : prix mensuel de chaque module au moment du paiement (MODULE=prix;...).
ALTER TABLE abo_paiement ADD COLUMN detail VARCHAR(500);
ALTER TABLE abo_paiement DROP COLUMN options;

UPDATE abo_formule SET description = 'Pour demarrer : une petite equipe.' WHERE nom = 'Essentiel';
UPDATE abo_formule SET description = 'Des limites confortables pour une entreprise qui grandit.' WHERE nom = 'Pro';
UPDATE abo_formule SET description = 'Sans aucune limite.' WHERE nom = 'Premium';
