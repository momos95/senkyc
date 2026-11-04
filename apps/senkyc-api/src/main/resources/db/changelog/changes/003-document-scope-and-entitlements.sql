--liquibase formatted sql

-- Périmètre produit : CNI, passeport et permis de conduire ; Sénégal d'abord, puis l'espace UEMOA.
-- Aucune ligne de verification ne peut exister avant cette migration (aucun endpoint ne les crée encore).

--changeset senkyc:010-verification-document-scope
ALTER TABLE verification
    ADD COLUMN document_type    varchar(24) NOT NULL,
    ADD COLUMN issuing_country  char(2)     NOT NULL;
ALTER TABLE verification
    ADD CONSTRAINT ck_verification_document_type
        CHECK (document_type IN ('NATIONAL_ID', 'PASSPORT', 'DRIVING_LICENSE')),
    -- Les 8 États de l'UEMOA ; les pays réellement ouverts sont décidés par l'application.
    ADD CONSTRAINT ck_verification_issuing_country
        CHECK (issuing_country IN ('SN', 'BJ', 'BF', 'CI', 'GW', 'ML', 'NE', 'TG'));
--rollback ALTER TABLE verification DROP CONSTRAINT ck_verification_issuing_country, DROP CONSTRAINT ck_verification_document_type, DROP COLUMN issuing_country, DROP COLUMN document_type;

--changeset senkyc:011-tenant-official-verification-entitlement
-- Version gratuite : pas de contrôle d'intégrité auprès des services officiels (connecteurs).
-- Faux par défaut ; l'application interdit VERIFIED_OFFICIAL tant que ce droit n'est pas accordé.
ALTER TABLE tenant ADD COLUMN official_verification_enabled boolean NOT NULL DEFAULT false;
--rollback ALTER TABLE tenant DROP COLUMN official_verification_enabled;
