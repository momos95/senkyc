--liquibase formatted sql

-- Isolation inter-tenants (semaine 2) :
--  * un rôle applicatif sans droit DDL (senkyc_app) distinct du compte de migration ;
--  * Row-Level Security : chaque ligne n'est visible que si tenant_id = app.tenant_id, paramètre de
--    session positionné par l'application (TenantAwareDataSource) à chaque emprunt de connexion ;
--  * fail-closed : paramètre absent ou vide => NULL => aucune ligne visible ni insérable.
-- Voir docs/adr/0009-contexte-tenant-et-rls.md.

--changeset senkyc:005-app-role splitStatements:false
DO $$
BEGIN
    -- En exploitation, le rôle est normalement créé par l'infrastructure (LOGIN + mot de passe).
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'senkyc_app') THEN
        CREATE ROLE senkyc_app NOLOGIN;
    END IF;
END
$$;

--changeset senkyc:006-app-grants
GRANT SELECT, INSERT, UPDATE ON tenant TO senkyc_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON verification TO senkyc_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON stored_object TO senkyc_app;
-- Audit en ajout seul : ni UPDATE ni DELETE pour le rôle applicatif.
GRANT SELECT, INSERT ON audit_event TO senkyc_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO senkyc_app;
--rollback REVOKE ALL ON tenant, verification, stored_object, audit_event FROM senkyc_app;

--changeset senkyc:007-rls-verification
ALTER TABLE verification ENABLE ROW LEVEL SECURITY;
ALTER TABLE verification FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON verification
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
--rollback DROP POLICY tenant_isolation ON verification;
--rollback ALTER TABLE verification NO FORCE ROW LEVEL SECURITY;
--rollback ALTER TABLE verification DISABLE ROW LEVEL SECURITY;

--changeset senkyc:008-rls-stored-object
ALTER TABLE stored_object ENABLE ROW LEVEL SECURITY;
ALTER TABLE stored_object FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON stored_object
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
--rollback DROP POLICY tenant_isolation ON stored_object;
--rollback ALTER TABLE stored_object NO FORCE ROW LEVEL SECURITY;
--rollback ALTER TABLE stored_object DISABLE ROW LEVEL SECURITY;

--changeset senkyc:009-rls-audit-event
ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_event FORCE ROW LEVEL SECURITY;
-- Un événement peut être global (tenant_id NULL) ou rattaché au tenant courant ; la lecture est
-- limitée au tenant courant.
CREATE POLICY audit_insert ON audit_event FOR INSERT
    WITH CHECK (tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY audit_select ON audit_event FOR SELECT
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
--rollback DROP POLICY audit_select ON audit_event;
--rollback DROP POLICY audit_insert ON audit_event;
--rollback ALTER TABLE audit_event NO FORCE ROW LEVEL SECURITY;
--rollback ALTER TABLE audit_event DISABLE ROW LEVEL SECURITY;

-- La table tenant n'a volontairement pas de RLS : elle est gérée par les administrateurs de la
-- plateforme (rôle senkyc-admin, contrôlé par l'API) et lue par id pour le tenant courant.
