# SenKYC — Senegal Know Your Customer 🇸🇳

Plateforme KYC open source, modulaire et auto-hébergeable pour vérifier **techniquement** une
identité à partir de **trois documents : CNI, passeport et permis de conduire** (capture, OCR, cohérence,
face matching, liveness). Périmètre : **Sénégal d'abord, puis l'espace UEMOA** (voir ADR 0010).

**Édition gratuite** : sans contrôle d'intégrité auprès des services officiels (connecteurs). Ce contrôle est
un droit accordé par tenant ; sans lui, le meilleur résultat possible est `APPROVED_TECHNICAL`.

> **Important.** Sans accès à un registre officiel autorisé, SenKYC ne certifie pas qu'une identité
> est authentique. Le statut `VERIFIED_OFFICIAL` n'est possible qu'avec une réponse positive d'un
> connecteur officiel réellement autorisé ; sinon le meilleur résultat est `APPROVED_TECHNICAL`.
> La conformité réglementaire (données personnelles, biométrie) n'est pas acquise par les seules mesures techniques.

**État : semaine 3 / 12 en cours** — socle, authentification multi-tenant (RLS) et stockage chiffré. Aucune vérification KYC n'est encore implémentée.

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
| Stockage S3 (RustFS) | optionnel, profil `storage` : API 9000, console 9001 (voir ADR 0007) |
| PostgreSQL | localhost:5432 |
| Service IA | non publié (réseau Compose uniquement) |

Vérifier la communication Java ↔ Python :

```bash
curl -s http://localhost:8080/actuator/health/readiness   # db + aiService doivent être UP
docker compose exec senkyc-ai python -c "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8000/health').read())"
```

### Obtenir un jeton et appeler l'API (DEV)

Les secrets sont dans `.env`. Après la mise à jour de la semaine 2, relancer `./scripts/init-env.sh`
(il n'ajoute que les variables manquantes), puis `docker compose down -v` : les rôles PostgreSQL et le
realm ne sont créés qu'à l'initialisation des volumes (**efface les données de dev**).

```bash
# Jeton du partenaire de démo (tenant demo-a) ; remplacer <secret> par PARTNER_DEMO_CLIENT_SECRET
curl -s -X POST http://localhost:8081/realms/senkyc/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=partner-demo -d client_secret=<secret>
# Puis (access_token dans la réponse) :
curl -s -H "Authorization: Bearer <access_token>" http://localhost:8080/api/v1/tenant
```

Administration plateforme : même requête avec `client_id=platform-admin` et `PLATFORM_ADMIN_CLIENT_SECRET`,
puis `GET /api/v1/admin/tenants`. Le jeton partenaire doit y recevoir 403.

Pour voir le détail des composants, démarrer l'API avec `SENKYC_HEALTH_DETAILS=always` (jamais en production).

### Vérifications (API)

| Méthode | Chemin | Rôle |
|---|---|---|
| POST | `/api/v1/verifications` | crée une demande (`Idempotency-Key` optionnelle) |
| POST | `/api/v1/verifications/{id}/documents` | envoie une image (multipart `part` + `file`, JPEG/PNG) |
| GET | `/api/v1/verifications/{id}` | statut et métadonnées |

L'envoi d'images exige le stockage chiffré (`SENKYC_STORAGE_ENABLED=true` et `docker compose --profile storage up -d`) ;
sinon l'API répond `503`. Détails et limites : ADR 0011.

## Développer sans Docker (API + service IA)

Prérequis : un PostgreSQL local (créer la base, le rôle propriétaire `senkyc` pour les migrations et un
rôle `senkyc_app` avec `LOGIN`), ainsi qu'un Keycloak joignable ou un jeton équivalent (JWKS configurable
via `SENKYC_JWT_*`). Variables : `SENKYC_DB_PASSWORD` (rôle `senkyc_app`), `SENKYC_MIGRATION_DB_PASSWORD`
(rôle `senkyc`).

```bash
# Terminal 1 : service IA (Python >= 3.11)
cd apps/senkyc-ai
python -m venv .venv && source .venv/bin/activate   # Windows : .venv\Scripts\activate
pip install .
export SENKYC_AI_INTERNAL_TOKEN=$(openssl rand -hex 16)   # Windows : set SENKYC_AI_INTERNAL_TOKEN=...
uvicorn senkyc_ai.main:create_app --factory --port 8000

# Terminal 2 : API (même jeton)
export SENKYC_DB_PASSWORD=...                       # mot de passe du rôle senkyc_app
export SENKYC_MIGRATION_DB_PASSWORD=...             # mot de passe du rôle senkyc (migrations)
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

[Apache License 2.0](LICENSE). Les licences des modèles IA (code **et** poids préentraînés) et de leurs
dépendances devront être vérifiées séparément avant leur intégration (semaines 4-7) : elles peuvent
différer de celle du projet.
