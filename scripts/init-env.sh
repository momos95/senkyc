#!/bin/sh
# Crée .env à partir de .env.example en remplaçant chaque "change-me" par un secret aléatoire.
# Si .env existe déjà, seules les variables MANQUANTES sont ajoutées (aucun secret existant n'est modifié).
set -eu
cd "$(dirname "$0")/.."

command -v openssl >/dev/null 2>&1 || { echo "openssl est requis." >&2; exit 1; }
umask 077

if [ ! -e .env ]; then
  while IFS= read -r line; do
    case "$line" in
      *=change-me) printf '%s=%s\n' "${line%%=*}" "$(openssl rand -hex 24)" ;;
      *) printf '%s\n' "$line" ;;
    esac
  done < .env.example > .env
  echo ".env généré (permissions 600)."
  exit 0
fi

added=0
while IFS= read -r line; do
  case "$line" in
    ''|'#'*) continue ;;
  esac
  key="${line%%=*}"
  if ! grep -q "^${key}=" .env; then
    case "$line" in
      *=change-me) value="$(openssl rand -hex 24)" ;;
      *) value="${line#*=}" ;;
    esac
    printf '%s=%s\n' "$key" "$value" >> .env
    echo "ajouté : $key"
    added=$((added + 1))
  fi
done < .env.example
echo ".env existant conservé ; $added variable(s) ajoutée(s)."
