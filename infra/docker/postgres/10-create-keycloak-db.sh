#!/bin/sh
# Exécuté une seule fois, à l'initialisation du volume PostgreSQL (docker compose down -v pour rejouer).
# Crée : la base et le rôle de Keycloak, et le rôle applicatif de l'API (sans droit DDL ; ses
# privilèges sont accordés par la migration Liquibase 002-tenant-isolation.sql).
set -eu
psql -v ON_ERROR_STOP=1 \
     -v kc_password="$KEYCLOAK_DB_PASSWORD" \
     -v app_password="$SENKYC_APP_DB_PASSWORD" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE keycloak LOGIN PASSWORD :'kc_password';
CREATE DATABASE keycloak OWNER keycloak;
CREATE ROLE senkyc_app LOGIN PASSWORD :'app_password';
EOSQL
