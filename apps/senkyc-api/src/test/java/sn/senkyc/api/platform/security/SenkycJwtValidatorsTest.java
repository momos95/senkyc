package sn.senkyc.api.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class SenkycJwtValidatorsTest {

    private static final String ISSUER = "http://localhost:8081/realms/senkyc";
    private static final String AUDIENCE = "senkyc-api";

    private final OAuth2TokenValidator<Jwt> validator = SenkycJwtValidators.create(ISSUER, AUDIENCE);

    private static Jwt.Builder validToken() {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject("partner")
                .audience(List.of(AUDIENCE))
                .claim(SenkycJwtValidators.TENANT_CLAIM, UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
    }

    @Test
    void acceptsAWellFormedTenantToken() {
        assertThat(validator.validate(validToken().build()).hasErrors()).isFalse();
    }

    @Test
    void rejectsAnotherIssuer() {
        Jwt jwt = validToken().issuer("http://evil.example/realms/senkyc").build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsAMissingOrWrongAudience() {
        assertThat(validator.validate(validToken().audience(List.of("other-api")).build()).hasErrors())
                .isTrue();
        Jwt noAudience = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .claim(SenkycJwtValidators.TENANT_CLAIM, UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        assertThat(validator.validate(noAudience).hasErrors()).isTrue();
    }

    @Test
    void rejectsAnExpiredToken() {
        Jwt jwt = validToken()
                .issuedAt(Instant.now().minusSeconds(3600))
                .expiresAt(Instant.now().minusSeconds(1800))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsATokenWithoutAValidTenant() {
        Jwt missing = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThat(validator.validate(missing).hasErrors()).isTrue();
        assertThat(validator.validate(validToken().claim("tenant_id", "not-a-uuid").build()).hasErrors())
                .isTrue();
    }

    @Test
    void platformAdministratorsDoNotNeedATenant() {
        Jwt admin = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .claim("realm_access", Map.of("roles", List.of(ApiPermissions.ADMIN_ROLE)))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThat(validator.validate(admin).hasErrors()).isFalse();
    }

    @Test
    void aMalformedRealmAccessClaimGrantsNothing() {
        Jwt jwt = validToken().claim("realm_access", "not-a-map").build();

        assertThat(SenkycJwtValidators.realmRoles(jwt)).isEmpty();
    }
}
