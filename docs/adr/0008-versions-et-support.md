# ADR 0008 — Versions et cycle de support

- Statut : accepté, à revérifier à chaque trimestre

| Composant | Choix | Remarque |
|---|---|---|
| Java | 21 (LTS) | Imposé |
| Spring Boot | 3.5.7 | Dernière ligne 3.x (imposée : « 3.x »). **Non vérifié en ligne** (Maven Central inaccessible lors de ce build) : prendre le dernier patch 3.5.x. |
| Keycloak | 26.4 | Ligne 26.x imposée |
| PostgreSQL | 17 | |
| Python | 3.12 dans l'image (>= 3.11 requis) | |

Point d'attention : d'après mes connaissances (non revérifiées), le support open source de Spring Boot
3.5 prend fin en 2026, et la ligne 4.x est la ligne courante. Prévoir la migration vers 4.x avant la
version stable ; cette migration aura un coût réel (changements de starters, de Jackson, des API de test) et doit être planifiée.
