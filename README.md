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
| MinIO console | optionnel, profil `storage` (image actuellement inaccessible, voir ADR 0007) |
| PostgreSQL | localhost:5432 |
| Service IA | non publié (réseau Compose uniquement) |

Vérifier la communication Java ↔ Python :

```bash
curl -s http://localhost:8080/actuator/health/readiness   # db + aiService doivent être UP
docker compose exec senkyc-ai python -c "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8000/health').read())"
```

Pour voir le détail des composants, démarrer l'API avec `SENKYC_HEALTH_DETAILS=always` (jamais en production).

## Développer sans Docker (API + service IA)

Prérequis : un PostgreSQL local (créer la base et le rôle `senkyc`).

```bash
# Terminal 1 : service IA (Python >= 3.11)
cd apps/senkyc-ai
python -m venv .venv && source .venv/bin/activate   # Windows : .venv\Scripts\activate
pip install .
export SENKYC_AI_INTERNAL_TOKEN=$(openssl rand -hex 16)   # Windows : set SENKYC_AI_INTERNAL_TOKEN=...
uvicorn senkyc_ai.main:create_app --factory --port 8000

# Terminal 2 : API (même jeton)
export SENKYC_DB_PASSWORD=...                       # mot de passe du rôle senkyc
export SENKYC_AI_INTERNAL_TOKEN=...                 # identique au terminal 1
mvn -B -pl apps/senkyc-api -am -DskipTests install
cd apps/senkyc-api && mvn spring-boot:run

curl http://localhost:8080/actuator/health/readiness   # {"status":"UP"}
```

## Tests

```bash
mvn -B test                                       # Java : tests unitaires uniquement (sans Docker)
mvn -B verify                                     # + tests d'intégration (Testcontainers : Docker requis, échec sinon)
cd apps/senkyc-ai && pip install ".[dev]" && ruff check . && pytest
docker compose config --quiet                     # validation de la syntaxe Compose
```

### Dépannage : `BadRequestException (Status 400)` dans Testcontainers

Docker Engine 29 refuse les clients utilisant une ancienne version de l'API, ce qui peut casser
Testcontainers 1.21.3 (« Could not find a valid Docker environment »). Contournement local :

```bash
echo "api.version=1.44" > ~/.docker-java.properties     # Windows : %USERPROFILE%\.docker-java.properties
```

Une version plus récente de Testcontainers corrige le problème à la source ; à valider avec
`mvn -Dtestcontainers.version=<version> verify` avant de modifier le `pom.xml`.

## Documentation

- [Architecture](docs/architecture/README.md) · [Modèle de données](docs/architecture/data-model.md)
- [Décisions d'architecture (ADR)](docs/adr/)

## Licence

À confirmer par les mainteneurs (fichier `LICENSE` à ajouter avant toute publication). Les licences des
modèles IA (code **et** poids) devront être vérifiées séparément avant leur intégration (semaines 4-7).
