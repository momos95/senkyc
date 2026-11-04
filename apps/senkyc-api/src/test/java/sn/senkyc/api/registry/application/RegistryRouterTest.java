package sn.senkyc.api.registry.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sn.senkyc.api.registry.application.RegistryRouter.Reason;
import sn.senkyc.api.verification.domain.DocumentType;
import sn.senkyc.api.verification.domain.IssuingCountry;
import sn.senkyc.registry.spi.IdentityRegistryProvider;
import sn.senkyc.registry.spi.RegistryVerificationRequest;
import sn.senkyc.registry.spi.RegistryVerificationResult;

class RegistryRouterTest {

    private static final IdentityRegistryProvider MOCK = new IdentityRegistryProvider() {
        @Override
        public String providerId() {
            return "mock";
        }

        @Override
        public boolean supports(String countryCode) {
            return true;
        }

        @Override
        public RegistryVerificationResult verify(RegistryVerificationRequest request) {
            return new RegistryVerificationResult(
                    RegistryVerificationResult.Outcome.UNAVAILABLE, "mock", Instant.EPOCH, null);
        }
    };

    // SN : CNI reliée à un connecteur, passeport sans connecteur ; les autres pays ne sont pas activés.
    private final RegistryRouter router = new RegistryRouter(Map.of(
            IssuingCountry.SN, Map.of(DocumentType.NATIONAL_ID, MOCK)));

    @Test
    void entitledTenantWithConfiguredConnectorGetsAnOfficialCheck() {
        RegistryRouter.Route route = router.route(true, IssuingCountry.SN, DocumentType.NATIONAL_ID);

        assertThat(route.officialCheck()).isTrue();
        assertThat(route.provider()).isSameAs(MOCK);
    }

    @Test
    void tenantWithoutEntitlementNeverGetsAnOfficialCheck() {
        RegistryRouter.Route route = router.route(false, IssuingCountry.SN, DocumentType.NATIONAL_ID);

        assertThat(route.officialCheck()).isFalse();
        assertThat(route.reason()).isEqualTo(Reason.TENANT_NOT_ENTITLED);
        assertThat(route.provider()).isNull();
    }

    @Test
    void documentWithoutConnectorFallsBackToCommonKycOnly() {
        RegistryRouter.Route route = router.route(true, IssuingCountry.SN, DocumentType.PASSPORT);

        assertThat(route.officialCheck()).isFalse();
        assertThat(route.reason()).isEqualTo(Reason.NO_CONNECTOR_FOR_DOCUMENT);
    }

    @Test
    void countryNotEnabledIsReportedSoTheCallerCanRejectTheVerification() {
        assertThat(router.isCountryEnabled(IssuingCountry.CI)).isFalse();
        assertThat(router.route(true, IssuingCountry.CI, DocumentType.NATIONAL_ID).reason())
                .isEqualTo(Reason.COUNTRY_NOT_ENABLED);
    }

    @Test
    void enabledCountryWithoutAnyConnectorIsValid() {
        RegistryRouter noConnectors = new RegistryRouter(Map.of(IssuingCountry.SN, Map.of()));

        assertThat(noConnectors.isCountryEnabled(IssuingCountry.SN)).isTrue();
        assertThat(noConnectors.route(true, IssuingCountry.SN, DocumentType.DRIVING_LICENSE).reason())
                .isEqualTo(Reason.NO_CONNECTOR_FOR_DOCUMENT);
    }
}
