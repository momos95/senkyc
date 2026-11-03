--liquibase formatted sql

-- Données de démonstration, appliquées UNIQUEMENT avec le contexte Liquibase "dev"
-- (SENKYC_LIQUIBASE_CONTEXTS=dev, positionné par docker-compose.yml). Le contexte par défaut est "prod".
-- Les identifiants correspondent au claim tenant_id des clients de démo de infra/keycloak/realm-senkyc.json.

--changeset senkyc:900-dev-seed-tenants context:dev
INSERT INTO tenant (id, code, name) VALUES
    ('00000000-0000-0000-0000-0000000000a1', 'demo-a', 'Tenant de démonstration A'),
    ('00000000-0000-0000-0000-0000000000a2', 'demo-b', 'Tenant de démonstration B')
ON CONFLICT DO NOTHING;
--rollback DELETE FROM tenant WHERE id IN ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000a2');
