package sn.senkyc.api.verification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Métadonnées d'une image chiffrée stockée (l'image elle-même n'est jamais en base). */
public record StoredDocument(
        UUID id,
        UUID tenantId,
        UUID verificationId,
        DocumentPart part,
        String bucket,
        String objectKey,
        String contentType,
        long sizeBytes,
        String sha256,
        String encryptionKeyId,
        Instant createdAt,
        Instant deleteAfter) {

    public StoredDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(verificationId, "verificationId");
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(objectKey, "objectKey");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(deleteAfter, "deleteAfter");
    }
}
