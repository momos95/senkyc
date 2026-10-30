# ADR 0005 — Multi-tenant : base partagée, `tenant_id` + Row-Level Security

- Statut : accepté pour la conception ; RLS en semaine 2

## Décision
Schéma unique, colonne `tenant_id` obligatoire, clés étrangères composites (déjà en place) et
Row-Level Security PostgreSQL pilotée par un paramètre de session positionné à chaque transaction
à partir du JWT. Rôle applicatif distinct du propriétaire du schéma.

## Alternatives écartées
Schéma par tenant / base par tenant : migrations et pooling multipliés.
