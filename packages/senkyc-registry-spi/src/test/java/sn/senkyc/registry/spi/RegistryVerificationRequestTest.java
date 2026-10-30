package sn.senkyc.registry.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RegistryVerificationRequestTest {

    // Données entièrement fictives.
    private static final String DOC = "TEST-0000001";

    private static RegistryVerificationRequest request(String country) {
        return new RegistryVerificationRequest(country, DOC, "Ndiaye", "Fatou", LocalDate.of(1990, 1, 2));
    }

    @Test
    void toStringNeverExposesPersonalData() {
        String text = request("SN").toString();

        assertThat(text).contains("SN", "<redacted>").doesNotContain(DOC, "Ndiaye", "Fatou", "1990");
    }

    @Test
    void normalizesCountryCodeToUpperCase() {
        assertThat(request("sn").countryCode()).isEqualTo("SN");
    }

    @Test
    void rejectsCountryCodeThatIsNotAlpha2() {
        assertThatThrownBy(() -> request("SEN")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request("S1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RegistryVerificationRequest("SN", null, "Ndiaye", "Fatou", LocalDate.of(1990, 1, 2)));
    }
}
