--liquibase formatted sql

-- Schéma initial SenKYC (semaine 1). Aucune donnée personnelle extraite de la CNI n'est
-- stockée ici : elle sera ajoutée chiffrée (semaine 4). Voir docs/architecture/data-model.md.

--changeset senkyc:001-tenant
CREATE TABLE tenant (
    id             uuid         PRIMARY KEY,
    code           varchar(64)  NOT NULL,
    name           varchar(200) NOT NULL,
    status         varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    -- Valeur de départ à valider avec le DPO / le cadre juridique applicable.
    retention_days integer      NOT NULL DEFAULT 30,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_code UNIQUE (code),
    CONSTRAINT ck_tenant_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_tenant_retention CHECK (retention_days BETWEEN 0 AND 3650)
);
--rollback DROP TABLE tenant;

--changeset senkyc:002-verification
CREATE TABLE verification (
    id                 uuid         PRIMARY KEY,
    tenant_id          uuid         NOT NULL REFERENCES tenant (id),
    external_reference varchar(128),
    idempotency_key    varchar(128),
    status             varchar(32)  NOT NULL,
    status_reason      varchar(64),
    expires_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    -- Cible de la clé étrangère composite de stored_object (isolation inter-tenants).
    CONSTRAINT uq_verification_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_verification_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'APPROVED_TECHNICAL', 'REJECTED', 'MANUAL_REVIEW',
        'VERIFIED_OFFICIAL', 'FAILED', 'EXPIRED', 'CANCELLED'))
);
CREATE UNIQUE INDEX uq_verification_idempotency
    ON verification (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_verification_tenant_created ON verification (tenant_id, created_at DESC);
CREATE INDEX ix_verification_expiry
    ON verification (expires_at) WHERE status IN ('PENDING', 'PROCESSING');
--rollback DROP TABLE verification;

--changeset senkyc:003-stored-object
CREATE TABLE stored_object (
    id                uuid         PRIMARY KEY,
    tenant_id         uuid         NOT NULL,
    verification_id   uuid         NOT NULL,
    kind              varchar(24)  NOT NULL,
    bucket            varchar(63)  NOT NULL,
    object_key        varchar(512) NOT NULL,
    content_type      varchar(100) NOT NULL,
    size_bytes        bigint       NOT NULL,
    sha256            char(64)     NOT NULL,
    encryption_key_id varchar(128) NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    delete_after      timestamptz  NOT NULL,
    deleted_at        timestamptz,
    CONSTRAINT fk_stored_object_verification FOREIGN KEY (tenant_id, verification_id)
        REFERENCES verification (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_stored_object_location UNIQUE (bucket, object_key),
    CONSTRAINT ck_stored_object_kind CHECK (kind IN ('DOCUMENT_FRONT', 'DOCUMENT_BACK', 'SELFIE')),
    CONSTRAINT ck_stored_object_size CHECK (size_bytes > 0)
);
CREATE INDEX ix_stored_object_verification ON stored_object (verification_id);
CREATE INDEX ix_stored_object_purge ON stored_object (delete_after) WHERE deleted_at IS NULL;
--rollback DROP TABLE stored_object;

--changeset senkyc:004-audit-event
CREATE TABLE audit_event (
    id             bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at    timestamptz  NOT NULL DEFAULT now(),
    -- Pas de clé étrangère : l'audit survit à la suppression d'un tenant.
    tenant_id      uuid,
    actor          varchar(200) NOT NULL,
    action         varchar(64)  NOT NULL,
    resource_type  varchar(48),
    resource_id    varchar(64),
    correlation_id varchar(64),
    -- Jamais de donnée biométrique ni de donnée d'identité en clair ici.
    details        jsonb        NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX ix_audit_event_tenant_time ON audit_event (tenant_id, occurred_at DESC);
--rollback DROP TABLE audit_event;
