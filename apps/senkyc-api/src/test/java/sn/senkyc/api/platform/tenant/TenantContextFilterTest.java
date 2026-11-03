package sn.senkyc.api.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    private Optional<UUID> run() throws Exception {
        AtomicReference<Optional<UUID>> seen = new AtomicReference<>();
        FilterChain chain = (request, response) -> seen.set(TenantContext.current());
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        return seen.get();
    }

    private static JwtAuthenticationToken token(String tenantClaim) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("partner")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (tenantClaim != null) {
            builder.claim(TenantContextFilter.TENANT_CLAIM, tenantClaim);
        }
        return new JwtAuthenticationToken(builder.build());
    }

    @Test
    void exposesTheTenantOfTheValidatedTokenThenClearsIt() throws Exception {
        UUID tenant = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(token(tenant.toString()));

        assertThat(run()).contains(tenant);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void hasNoTenantWithoutAuthentication() throws Exception {
        assertThat(run()).isEmpty();
    }

    @Test
    void ignoresAMissingOrMalformedClaim() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(token(null));
        assertThat(run()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(token("not-a-uuid"));
        assertThat(run()).isEmpty();
    }
}
