# ADR 0009 — Contexte tenant et Row-Level Security

- Statut : implémenté en semaine 2 ; tests d'intégration à exécuter par un humain (Docker requis)

## Décision
- Le `tenant_id` vient **uniquement** du jeton validé (jamais d'un paramètre ou d'un en-tête).
  `TenantContextFilter` le place dans un `ThreadLocal` (`TenantContext`) et le nettoie en `finally`.
- Chaque connexion obtenue du pool reçoit `set_config('app.tenant_id', <uuid ou vide>, false)`
  (`TenantAwareDataSource`). Sans tenant, la valeur vide ne correspond à **aucune** ligne (fail-closed).
- Les politiques RLS (`USING` + `WITH CHECK`, `ENABLE` + `FORCE`) comparent `tenant_id` à cette valeur.
- Deux comptes : propriétaire (migrations Liquibase) et `senkyc_app` (requêtes, sans DDL ; `audit_event`
  en SELECT/INSERT seulement).
- Contexte Liquibase `prod` par défaut ; les tenants de démonstration (`dev`) ne s'appliquent que si demandé.

## Alternatives écartées
Filtre Hibernate / dialecte personnalisé (moins fiable que la base comme garde-fou), schéma par tenant
(coût d'exploitation).

## Limites connues
- Un superuser PostgreSQL contourne la RLS : l'application ne doit jamais s'y connecter.
- `set_config(..., false)` persiste sur la connexion : il est réécrit à chaque emprunt.
- L'isolation n'est démontrée que par les tests d'intégration, non exécutés dans l'environnement de rédaction.
