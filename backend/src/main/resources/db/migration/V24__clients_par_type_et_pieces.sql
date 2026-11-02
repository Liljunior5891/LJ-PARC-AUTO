-- =====================================================================
-- PARC AUTO - V24 : fiche client adaptee a son type et pieces justificatives
--
-- Un particulier se decrit par son nom, ses prenoms et sa piece d'identite ;
-- une entreprise par sa forme juridique, son RCCM et sa carte d'operateur
-- economique ; une administration par son organisme de tutelle ; une ONG par
-- son agrement. Les pieces sont des documents rattaches au client.
-- =====================================================================

ALTER TABLE client ADD COLUMN nom               VARCHAR(80);
ALTER TABLE client ADD COLUMN prenoms           VARCHAR(120);
ALTER TABLE client ADD COLUMN date_naissance    DATE;
ALTER TABLE client ADD COLUMN profession        VARCHAR(100);
ALTER TABLE client ADD COLUMN forme_juridique   VARCHAR(20);
ALTER TABLE client ADD COLUMN rccm              VARCHAR(60);
ALTER TABLE client ADD COLUMN secteur_activite  VARCHAR(100);
ALTER TABLE client ADD COLUMN fonction_contact  VARCHAR(100);
ALTER TABLE client ADD COLUMN organisme_tutelle VARCHAR(150);
ALTER TABLE client ADD COLUMN numero_agrement   VARCHAR(60);
