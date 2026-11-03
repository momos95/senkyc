# ADR 0004 — Authentification : Keycloak, client credentials, tenant par claim

- Statut : implémenté en semaine 2 (non validé contre un Keycloak réel au moment de l'écriture : voir README)

## Décision
- Un **realm unique** `senkyc`. Chaque partenaire = un client confidentiel (client credentials).
  Le claim `tenant_id` est ajouté au jeton ; l'API le traite comme la source de vérité du tenant.
  (Un realm par tenant a été écarté : coût d'exploitation, pas de besoin d'isolation IAM stricte.)
- L'API est un OAuth2 Resource Server (validation JWT via JWKS). Les rôles (RBAC) et scopes API
  (`kyc:verifications:read|write`) seront définis en semaine 2, **testés contre un Keycloak réel**.
- Widget : jeton court à usage unique émis par l'API, lié à une vérification et à un tenant.
- Le realm importé en semaine 1 est volontairement minimal (client `partner-demo`, rôles, claim
  `tenant_id` codé en dur pour le DEV). Son secret provient de l'environnement (`${PARTNER_DEMO_CLIENT_SECRET}`).

## Mise en œuvre (semaine 2)
- Les permissions API sont portées par des **rôles realm** (`kyc:verifications:read|write`) ; l'API
  accepte aussi les scopes (`SCOPE_`). Raison : comportement de l'import de realm avec des scopes
  personnalisés non vérifié ; les rôles évitent cette dépendance.
- Validation du jeton : signature RS256 (JWKS), expiration, `iss`, `aud` = `senkyc-api`, et `tenant_id`
  (UUID) obligatoire, sauf pour le rôle `senkyc-admin` (administration plateforme, sans tenant).
- `iss` (URL publique) et URL JWKS (interne à Docker) sont configurés séparément.
- Clients de démonstration DEV : `partner-demo` (tenant fixe) et `platform-admin`.

## Conséquences
Le provisionnement automatique des clients Keycloak par tenant reste à faire (le tenant est créé en
base via `POST /api/v1/admin/tenants`, mais le client OAuth2 est créé à la main dans Keycloak).
