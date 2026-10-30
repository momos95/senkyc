package sn.senkyc.api.platform.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import sn.senkyc.api.platform.web.CorrelationIdFilter;

/** Vérifie la posture fail-closed : tout est refusé sans authentification, hors santé. */
@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestsAreRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/verifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/verifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownActuatorEndpointsAreNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void correlationIdIsPresentEvenOnRejectedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/verifications"))
                .andExpect(header().exists(CorrelationIdFilter.HEADER));
    }
}
