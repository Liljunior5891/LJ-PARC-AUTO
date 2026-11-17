#!/bin/sh
# Genere .env (base) et api.env (API) une seule fois, sur le serveur. Ne reecrit jamais un fichier existant.
set -e
cd "$(dirname "$0")"
umask 077
alea() { openssl rand -base64 "$1" | tr -d '\n/+=' ; }
if [ ! -f .env ]; then
  cat > .env <<EOF
POSTGRES_PASSWORD=$(alea 36)
NOVA_OWNER_PASSWORD=$(alea 36)
NOVA_APP_PASSWORD=$(alea 36)
EOF
  echo ".env cree"
fi
if [ ! -f api.env ]; then
  cat > api.env <<EOF
JWT_SECRET=$(openssl rand -base64 96 | tr -d '\n')
DEUX_FACTEURS_CLE=$(openssl rand -base64 48 | tr -d '\n')
WHATSAPP_CLE=$(openssl rand -base64 48 | tr -d '\n')
PRESENCE_CLE_PIN=$(openssl rand -base64 48 | tr -d '\n')
PLATEFORME_ADMIN_EMAIL=$1
PLATEFORME_ADMIN_PASSWORD=Nova-$(alea 24)-9a
NIVEAU_LOG=INFO
EOF
  echo "api.env cree"
fi
chmod 600 .env api.env
