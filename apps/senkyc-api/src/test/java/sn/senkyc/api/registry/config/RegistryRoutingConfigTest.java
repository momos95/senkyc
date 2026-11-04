package sn.senkyc.api.registry.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sn.senkyc.api.registry.application.RegistryRouter;
import sn.senkyc.api.verification.domain.DocumentType;
import sn.senkyc.api.verification.domain.IssuingCountry;
import sn.senkyc.registry.spi.IdentityRegistryProvider;
import sn.senkyc.registry.spi.RegistryVerificationRequest;
import sn.senkyc.registry.spi.RegistryVerificationResult;

/** Valide la liaison pays/document -> connecteur et les échecs au démarrage (fail-fast). */
class RegistryRoutingConfigTest {

    static class FakeProvider implements IdentityRegistryProvider {
        private final String id;
        private final String country;

        FakeProvider(String id, String country) {
            this.id = id;
            this.country = country;
        }

        @Override
        public String providerId() {
            return id;
        }

        @Override
        public boolean supports(String countryCode) {
            return country.equals(countryCode);
        }

        @Override
        public RegistryVerificationResult verify(RegistryVerificationRequest request) {
            return new RegistryVerificationResult(
                    RegistryVerificationResult.Outcome.UNAVAILABLE, id, Instant.EPOCH, null);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProvidersConfig {
        @Bean
        IdentityRegistryProvider sn() {
            return new FakeProvider("fake-sn", "SN");
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RegistryRoutingConfig.class, ProvidersConfig.class);

    @Test
    void countryWithoutDocumentsStartsAndHasNoOfficialCheck() {
        runner.withPropertyValues("senkyc.countries.SN.enabled=true").run(context -> {
            RegistryRouter router = context.getBean(RegistryRouter.class);
            assertThat(router.isCountryEnabled(IssuingCountry.SN)).isTrue();
            assertThat(router.route(true, IssuingCountry.SN, DocumentType.NATIONAL_ID).officialCheck()).isFalse();
        });
    }

    @Test
    void configuredConnectorIsRoutedPerDocumentType() {
        runner.withPropertyValues(
                        "senkyc.countries.SN.enabled=true",
                        "senkyc.countries.SN.documents.NATIONAL_ID.connector=fake-sn")
                .run(context -> {
                    RegistryRouter router = context.getBean(RegistryRouter.class);
                    assertThat(router.route(true, IssuingCountry.SN, DocumentType.NATIONAL_ID).officialCheck())
                            .isTrue();
                    assertThat(router.route(true, IssuingCountry.SN, DocumentType.PASSPORT).officialCheck())
                            .isFalse();
                });
    }

    @Test
    void disabledCountryIsTreatedAsAbsent() {
        runner.withPropertyValues("senkyc.countries.SN.enabled=false").run(context ->
                assertThat(context.getBean(RegistryRouter.class).isCountryEnabled(IssuingCountry.SN)).isFalse());
    }

    @Test
    void unknownConnectorFailsStartup() {
        runner.withPropertyValues(
                        "senkyc.countries.SN.enabled=true",
                        "senkyc.countries.SN.documents.NATIONAL_ID.connector=does-not-exist")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void connectorThatDoesNotSupportTheCountryFailsStartup() {
        runner.withPropertyValues(
                        "senkyc.countries.CI.enabled=true",
                        "senkyc.countries.CI.documents.NATIONAL_ID.connector=fake-sn")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void emptyConnectorFailsStartup() {
        runner.withPropertyValues(
                        "senkyc.countries.SN.enabled=true", "senkyc.countries.SN.documents.NATIONAL_ID.connector=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void unknownCountryOrDocumentTypeFailsStartup() {
        runner.withPropertyValues("senkyc.countries.XX.enabled=true").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(
                        "senkyc.countries.SN.enabled=true",
                        "senkyc.countries.SN.documents.VOTER_CARD.connector=fake-sn")
                .run(context -> assertThat(context).hasFailed());
    }
}
