package sn.senkyc.registry.spi;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Données envoyées à un registre. Contient des données personnelles : {@link #toString()} les
 * masque volontairement pour éviter toute fuite dans les journaux.
 */
public record RegistryVerificationRequest(
        String countryCode,
        String documentNumber,
        String lastName,
        String firstName,
        LocalDate birthDate) {

    private static final Pattern ISO_ALPHA2 = Pattern.compile("^[A-Za-z]{2}$");

    public RegistryVerificationRequest {
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(documentNumber, "documentNumber");
        Objects.requireNonNull(lastName, "lastName");
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(birthDate, "birthDate");
        if (!ISO_ALPHA2.matcher(countryCode).matches()) {
            throw new IllegalArgumentException("countryCode must be an ISO 3166-1 alpha-2 code");
        }
        countryCode = countryCode.toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RegistryVerificationRequest[countryCode=" + countryCode + ", personalData=<redacted>]";
    }
}
