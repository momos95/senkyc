# ADR 0004 — Authentification : Keycloak, client credentials, tenant par claim

- Statut : accepté pour la conception ; implémentation en semaine 2

## Décision
- Un **realm unique** `senkyc`. Chaque partenaire = un client confidentiel (client credentials).
  Le claim `tenant_id` est ajouté au jeton ; l'API le traite comme la source de vérité du tenant.
  (Un realm par tenant a été écarté : coût d'exploitation, pas de besoin d'isolation IAM stricte.)
- L'API est un OAuth2 Resource Server (validation JWT via JWKS). Les rôles (RBAC) et scopes API
  (`kyc:verifications:read|write`) seront définis en semaine 2, **testés contre un Keycloak réel**.
- Widget : jeton court à usage unique émis par l'API, lié à une vérification et à un tenant.
- Le realm importé en semaine 1 est volontairement minimal (client `partner-demo`, rôles, claim
  `tenant_id` codé en dur pour le DEV). Son secret provient de l'environnement (`${PARTNER_DEMO_CLIENT_SECRET}`).

## Conséquences
Le provisionnement automatique des clients/tenants (tenant-management) arrive en semaine 2.
