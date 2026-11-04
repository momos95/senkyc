package sn.senkyc.api.verification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Une demande de vérification d'identité, rattachée à un tenant. */
public record Verification(
        UUID id,
        UUID tenantId,
        String externalReference,
        String idempotencyKey,
        VerificationStatus status,
        DocumentType documentType,
        IssuingCountry issuingCountry,
        Instant expiresAt,
        Instant createdAt,
        long version) {

    public Verification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(issuingCountry, "issuingCountry");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Accepte encore des documents : en attente et non expirée. */
    public boolean isOpenAt(Instant now) {
        return status == VerificationStatus.PENDING && now.isBefore(expiresAt);
    }

    /** Une requête rejouée avec la même clé d'idempotence doit décrire exactement la même demande. */
    public boolean sameRequestAs(DocumentType type, IssuingCountry country, String reference) {
        return documentType == type && issuingCountry == country && Objects.equals(externalReference, reference);
    }
}
