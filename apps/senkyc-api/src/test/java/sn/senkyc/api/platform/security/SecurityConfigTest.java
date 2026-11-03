package sn.senkyc.api.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import sn.senkyc.api.platform.pagination.PageResult;
import sn.senkyc.api.platform.web.CorrelationIdFilter;
import sn.senkyc.api.tenant.application.TenantService;

/** Vérifie la posture fail-closed et les règles d'autorisation (sans Keycloak ni base de données). */
@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private TenantService tenantService;

    private static RequestPostProcessor withAuthorities(String... authorities) {
        GrantedAuthority[] granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(GrantedAuthority[]::new);
        return jwt().authorities(granted);
    }

    private int statusOf(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void requestsWithoutTokenAreRejectedWith401AndABearerChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/verifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
        mockMvc.perform(post("/api/v1/verifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokensAreRejectedWith401() throws Exception {
        when(jwtDecoder.decode("bad")).thenThrow(new BadJwtException("invalid"));

        mockMvc.perform(get("/api/v1/tenant").header(HttpHeaders.AUTHORIZATION, "Bearer bad"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownActuatorEndpointsAreNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void correlationIdIsPresentEvenOnRejectedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/verifications")).andExpect(header().exists(CorrelationIdFilter.HEADER));
    }

    @Test
    void verificationsReadNeedsTheReadPermission() throws Exception {
        assertThat(statusOf(get("/api/v1/verifications").with(withAuthorities()))).isEqualTo(403);
        assertThat(statusOf(get("/api/v1/verifications").with(withAuthorities("SCOPE_kyc:verifications:write"))))
                .isEqualTo(403);
        // Autorisé : la sécurité laisse passer (il n'existe pas encore de contrôleur => ni 401 ni 403).
        assertThat(statusOf(get("/api/v1/verifications").with(withAuthorities("SCOPE_kyc:verifications:read"))))
                .isNotIn(401, 403);
        assertThat(statusOf(get("/api/v1/verifications").with(withAuthorities("ROLE_kyc:verifications:read"))))
                .isNotIn(401, 403);
    }

    @Test
    void verificationsWriteNeedsTheWritePermission() throws Exception {
        assertThat(statusOf(post("/api/v1/verifications").with(withAuthorities("SCOPE_kyc:verifications:read"))))
                .isEqualTo(403);
        assertThat(statusOf(post("/api/v1/verifications").with(withAuthorities("ROLE_kyc:verifications:write"))))
                .isNotIn(401, 403);
    }

    @Test
    void adminEndpointsAreReservedToPlatformAdministrators() throws Exception {
        when(tenantService.list(0, 20)).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/admin/tenants").with(withAuthorities("ROLE_kyc-partner")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/tenants").with(withAuthorities("ROLE_senkyc-admin")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void anythingNotExplicitlyAllowedIsDenied() throws Exception {
        assertThat(statusOf(get("/api/v1/unknown").with(withAuthorities("ROLE_senkyc-admin"))))
                .isEqualTo(403);
    }
}
