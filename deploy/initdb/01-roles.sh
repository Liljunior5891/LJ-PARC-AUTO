#!/bin/sh
# Premier demarrage de la base : deux roles (docs/mise-en-production.md, section 5).
#  - nova_owner : proprietaire des tables, sert seulement aux migrations Flyway ;
#  - nova_app   : role de l'API, sans superutilisateur, lecture et ecriture des donnees.
# Les droits de nova_app sur les tables futures viennent des privileges par defaut ;
# les journaux immuables sont restreints apres la premiere migration (journaux-immuables.sql).
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v owner_pw="$NOVA_OWNER_PASSWORD" -v app_pw="$NOVA_APP_PASSWORD" <<'SQL'
CREATE ROLE nova_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'owner_pw';
CREATE ROLE nova_app   LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_pw';
ALTER DATABASE nova OWNER TO nova_owner;
ALTER SCHEMA public OWNER TO nova_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO nova_app;
REVOKE CONNECT ON DATABASE nova FROM PUBLIC;
GRANT CONNECT ON DATABASE nova TO nova_owner, nova_app;
ALTER DEFAULT PRIVILEGES FOR ROLE nova_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO nova_app;
ALTER DEFAULT PRIVILEGES FOR ROLE nova_owner IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO nova_app;
ALTER DEFAULT PRIVILEGES FOR ROLE nova_owner IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO nova_app;
SQL
