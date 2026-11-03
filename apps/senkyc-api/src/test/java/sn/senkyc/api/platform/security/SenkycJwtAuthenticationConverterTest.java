package sn.senkyc.api.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SenkycJwtAuthenticationConverterTest {

    private final SenkycJwtAuthenticationConverter converter = new SenkycJwtAuthenticationConverter();

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("partner-demo")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
    }

    private static List<String> names(Iterable<? extends GrantedAuthority> authorities) {
        return java.util.stream.StreamSupport.stream(authorities.spliterator(), false)
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Test
    void mapsScopesAndRealmRoles() {
        Jwt jwt = token().claim("scope", "kyc:verifications:read kyc:verifications:write")
                .claim("realm_access", Map.of("roles", List.of("kyc-partner", "senkyc-admin")))
                .build();

        var authentication = converter.convert(jwt);

        assertThat(names(authentication.getAuthorities()))
                .containsExactlyInAnyOrder(
                        "SCOPE_kyc:verifications:read",
                        "SCOPE_kyc:verifications:write",
                        "ROLE_kyc-partner",
                        "ROLE_senkyc-admin");
        assertThat(authentication.getName()).isEqualTo("partner-demo");
    }

    @Test
    void aTokenWithoutScopesOrRolesHasNoAuthority() {
        assertThat(converter.convert(token().build()).getAuthorities()).isEmpty();
    }
}
