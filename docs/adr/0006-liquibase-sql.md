# ADR 0006 — Migrations Liquibase en « formatted SQL »

- Statut : accepté (semaine 1)

## Décision
Master changelog YAML qui inclut des fichiers SQL annotés (`--changeset`, `--rollback`). Le SQL est
lisible et relu comme tel (index partiels, contraintes `CHECK`, types PostgreSQL). Hibernate
en `ddl-auto: validate` : il ne modifie jamais le schéma. Un changeset appliqué n'est jamais édité.
