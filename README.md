# SenKYC — Senegal Know Your Customer 🇸🇳

Plateforme KYC open source, modulaire et auto-hébergeable pour vérifier **techniquement** une
identité au Sénégal (CNI : capture, OCR, cohérence, face matching, liveness).

> **Important.** Sans accès à un registre officiel autorisé, SenKYC ne certifie pas qu'une identité
> est authentique. Le statut `VERIFIED_OFFICIAL` n'est possible qu'avec une réponse positive d'un
> connecteur officiel réellement autorisé ; sinon le meilleur résultat est `APPROVED_TECHNICAL`.
> La conformité réglementaire (données personnelles, biométrie) n'est pas acquise par les seules mesures techniques.

**État : semaine 1 / 12 — socle technique uniquement.** Aucune fonctionnalité KYC n'est encore implémentée.

## Prérequis

Java 21, Maven 3.9+, Python 3.11+, Docker + Docker Compose v2.

## Démarrer (Docker Compose, développement)

```bash
./scripts/init-env.sh              # génère .env avec des secrets aléatoires
docker compose up -d --build
docker compose ps                  # attendre « healthy »
```

| Service | URL (liée à 127.0.0.1) |
|---|---|
| API | http://localhost:8080/actuator/health |
| Keycloak | http://localhost:8081 (admin : voir `.env`) |
| MinIO console | http://localhost:9001 |
| PostgreSQL | localhost:5432 |
| Service IA | non publié (réseau Compose uniquement) |

Vérifier la communication Java ↔ Python :

```bash
curl -s http://localhost:8080/actuator/health/readiness   # db + aiService doivent être UP
docker compose exec senkyc-ai python -c "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8000/health').read())"
```

Pour voir le détail des composants, démarrer l'API avec `SENKYC_HEALTH_DETAILS=always` (jamais en production).

## Développer sans conteneur pour l'API

```bash
# Le service IA n'est pas publié par défaut : créer un docker-compose.override.yml local (non versionné) contenant
#   services: { senkyc-ai: { ports: ["127.0.0.1:8000:8000"] } }
docker compose up -d postgres senkyc-ai
set -a; . ./.env; set +a
export SENKYC_DB_PASSWORD="$POSTGRES_PASSWORD"
export SENKYC_AI_BASE_URL=http://localhost:8000
export SENKYC_AI_INTERNAL_TOKEN                   # déjà chargé depuis .env
mvn -pl apps/senkyc-api -am spring-boot:run
```

## Tests

```bash
mvn -B verify                                     # Java : unitaires (surefire) + intégration (failsafe, Testcontainers : Docker requis, sinon ignorés)
cd apps/senkyc-ai && pip install ".[dev]" && ruff check . && pytest
docker compose config --quiet                     # validation de la syntaxe Compose
```

## Documentation

- [Architecture](docs/architecture/README.md) · [Modèle de données](docs/architecture/data-model.md)
- [Décisions d'architecture (ADR)](docs/adr/)

## Licence

À confirmer par les mainteneurs (fichier `LICENSE` à ajouter avant toute publication). Les licences des
modèles IA (code **et** poids) devront être vérifiées séparément avant leur intégration (semaines 4-7).
