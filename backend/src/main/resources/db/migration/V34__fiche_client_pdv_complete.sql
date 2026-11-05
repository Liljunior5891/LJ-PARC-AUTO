-- Fiche client du point de vente : memes rubriques que celle de la gestion de parc.
-- Les clients existants gardent leur nom ; les nouveaux champs restent vides.
ALTER TABLE pdv_client
    ADD COLUMN nom_famille             VARCHAR(70),
    ADD COLUMN prenoms                 VARCHAR(70),
    ADD COLUMN date_naissance          DATE,
    ADD COLUMN profession              VARCHAR(100),
    ADD COLUMN forme_juridique         VARCHAR(40),
    ADD COLUMN rccm                    VARCHAR(60),
    ADD COLUMN secteur_activite        VARCHAR(100),
    ADD COLUMN organisme_tutelle       VARCHAR(150),
    ADD COLUMN numero_agrement         VARCHAR(60),
    ADD COLUMN ville                   VARCHAR(80),
    ADD COLUMN pays                    VARCHAR(80),
    ADD COLUMN personne_contact        VARCHAR(120),
    ADD COLUMN fonction_contact        VARCHAR(100),
    ADD COLUMN telephone_contact       VARCHAR(40),
    ADD COLUMN conditions_commerciales TEXT;

ALTER TABLE pdv_client DROP CONSTRAINT ck_pdv_client_type;
ALTER TABLE pdv_client ADD CONSTRAINT ck_pdv_client_type
    CHECK (type IN ('PARTICULIER', 'ARTISAN', 'ENTREPRISE', 'ADMINISTRATION', 'ONG'));
