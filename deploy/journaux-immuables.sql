-- Apres chaque migration : les journaux restent en ajout seul pour le role de l'API.
REVOKE UPDATE, DELETE ON agc_journal_audit, agc_ecriture, agc_ligne_registre, journal_activite FROM nova_app;
-- Flyway n'appartient qu'au proprietaire.
REVOKE ALL ON flyway_schema_history FROM nova_app;
