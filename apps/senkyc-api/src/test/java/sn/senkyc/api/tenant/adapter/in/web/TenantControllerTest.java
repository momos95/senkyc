package sn.senkyc.api.tenant.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import sn.senkyc.api.platform.security.SecurityConfig;
import sn.senkyc.api.platform.tenant.TenantContextMissingException;
import sn.senkyc.api.tenant.application.TenantService;
import sn.senkyc.api.tenant.domain.DuplicateTenantCodeException;
import sn.senkyc.api.tenant.domain.Tenant;
import sn.senkyc.api.tenant.domain.TenantStatus;

@WebMvcTest(TenantController.class)
@Import(SecurityConfig.class)
class TenantControllerTest {

    private static final Tenant TENANT = new Tenant(
            UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
            "demo-a",
            "Tenant A",
            TenantStatus.ACTIVE,
            30,
            false,
            Instant.parse("2026-11-03T10:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private TenantService tenantService;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_senkyc-admin"));
    }

    @Test
    void returnsTheCurrentTenant() throws Exception {
        when(tenantService.currentTenant()).thenReturn(TENANT);

        mockMvc.perform(get("/api/v1/tenant").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("demo-a"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.retentionDays").value(30));
    }

    @Test
    void aTokenWithoutTenantGets403WithAProblemDetail() throws Exception {
        when(tenantService.currentTenant()).thenThrow(new TenantContextMissingException());

        mockMvc.perform(get("/api/v1/tenant").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Tenant required"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void createsATenant() throws Exception {
        when(tenantService.createTenant(any())).thenReturn(TENANT);

        mockMvc.perform(post("/api/v1/admin/tenants")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"demo-a\",\"name\":\"Tenant A\",\"retentionDays\":30}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TENANT.id().toString()));
    }

    @Test
    void rejectsAnInvalidCreationRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tenants")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"Bad Code!\",\"name\":\"\",\"retentionDays\":99999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void reportsADuplicateCodeAs409() throws Exception {
        when(tenantService.createTenant(any())).thenThrow(new DuplicateTenantCodeException("demo-a"));

        mockMvc.perform(post("/api/v1/admin/tenants")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"demo-a\",\"name\":\"Tenant A\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Tenant code already used"));
    }

    @Test
    void rejectsAnOutOfRangePageSize() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tenants").param("size", "1000").with(admin()))
                .andExpect(status().isBadRequest());
    }
}
