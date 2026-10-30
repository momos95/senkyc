package sn.senkyc.registry.spi;

import java.time.Instant;
import java.util.Objects;

/**
 * Réponse d'un registre. Seul {@link Outcome#MATCH} renvoyé par un fournisseur réellement
 * autorisé peut conduire, côté moteur de décision, au statut {@code VERIFIED_OFFICIAL}.
 *
 * @param providerReference référence opaque côté fournisseur (peut être {@code null})
 */
public record RegistryVerificationResult(
        Outcome outcome,
        String providerId,
        Instant verifiedAt,
        String providerReference) {

    public enum Outcome {
        /** Le registre confirme que les données correspondent à une identité connue. */
        MATCH,
        /** Le registre répond mais ne confirme pas les données. */
        NO_MATCH,
        /** Registre injoignable, non configuré ou en erreur : jamais une confirmation. */
        UNAVAILABLE
    }

    public RegistryVerificationResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
    }
}
