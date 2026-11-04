--liquibase formatted sql

--changeset senkyc:012-stored-object-one-part-per-verification
-- Une seule image active par partie (recto, verso, selfie) et par vérification.
CREATE UNIQUE INDEX uq_stored_object_part ON stored_object (verification_id, kind) WHERE deleted_at IS NULL;
--rollback DROP INDEX uq_stored_object_part;
