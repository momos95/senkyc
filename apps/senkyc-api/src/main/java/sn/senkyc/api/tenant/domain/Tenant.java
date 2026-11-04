package sn.senkyc.api.tenant.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un client de la plateforme (banque, fintech, assurance...). Tout le reste lui est rattaché.
 *
 * <p>{@code officialVerificationEnabled} : droit au contrôle d'intégrité auprès des services officiels
 * (connecteurs). Faux dans la version gratuite.
 */
public record Tenant(
        UUID id,
        String code,
        String name,
        TenantStatus status,
        int retentionDays,
        boolean officialVerificationEnabled,
        Instant createdAt) {

    public Tenant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (retentionDays < 0 || retentionDays > 3650) {
            throw new IllegalArgumentException("retentionDays must be between 0 and 3650");
        }
    }
}
