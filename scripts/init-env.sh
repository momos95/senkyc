#!/bin/sh
# Génère .env à partir de .env.example en remplaçant chaque "change-me" par un secret aléatoire.
set -eu
cd "$(dirname "$0")/.."

if [ -e .env ]; then
  echo ".env existe déjà : rien n'a été modifié." >&2
  exit 1
fi
command -v openssl >/dev/null 2>&1 || { echo "openssl est requis." >&2; exit 1; }

umask 077
while IFS= read -r line; do
  case "$line" in
    *=change-me) printf '%s=%s\n' "${line%%=*}" "$(openssl rand -hex 24)" ;;
    *) printf '%s\n' "$line" ;;
  esac
done < .env.example > .env
echo ".env généré (permissions 600)."
