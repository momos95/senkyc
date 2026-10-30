#!/bin/sh
# Exécuté une seule fois, à l'initialisation du volume PostgreSQL.
set -eu
psql -v ON_ERROR_STOP=1 -v kc_password="$KEYCLOAK_DB_PASSWORD" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE keycloak LOGIN PASSWORD :'kc_password';
CREATE DATABASE keycloak OWNER keycloak;
EOSQL
