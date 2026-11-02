-- =====================================================================
-- PARC AUTO - V25 : fiche chauffeur adaptee a son type
--
-- Un chauffeur salarie a un contrat (CDI ou CDD), un salaire et un numero
-- CNSS ; un journalier est paye a la journee ; un prestataire independant
-- facture au jour, au voyage ou au mois et a son propre NIF. Le groupe
-- sanguin sert en cas d'accident. Les chauffeurs existants sont salaries.
-- =====================================================================

ALTER TABLE chauffeur ADD COLUMN type_chauffeur     VARCHAR(20) NOT NULL DEFAULT 'SALARIE';
ALTER TABLE chauffeur ADD COLUMN type_contrat       VARCHAR(10);
ALTER TABLE chauffeur ADD COLUMN date_fin_contrat   DATE;
ALTER TABLE chauffeur ADD COLUMN numero_cnss        VARCHAR(40);
ALTER TABLE chauffeur ADD COLUMN tarif              NUMERIC(16, 2);
ALTER TABLE chauffeur ADD COLUMN unite_tarif        VARCHAR(10);
ALTER TABLE chauffeur ADD COLUMN identifiant_fiscal VARCHAR(60);
ALTER TABLE chauffeur ADD COLUMN groupe_sanguin     VARCHAR(5);

ALTER TABLE chauffeur
    ADD CONSTRAINT ck_chauffeur_type CHECK (type_chauffeur IN ('SALARIE', 'JOURNALIER', 'PRESTATAIRE'));
