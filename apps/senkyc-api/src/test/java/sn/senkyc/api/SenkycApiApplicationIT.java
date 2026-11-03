package sn.senkyc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sn.senkyc.api.platform.ai.AiServiceClient;
import sn.senkyc.api.platform.ai.AiServiceInfo;

/**
 * Test d'intégration avec un vrai PostgreSQL (Testcontainers). Docker est requis et son absence
 * fait échouer le test (jamais d'ignorance silencieuse) ; {@code mvn test} n'exécute que les tests
 * unitaires. Le service IA est simulé : l'appel HTTP réel est couvert par
 * {@code RestAiServiceClientTest} et par la vérification Compose.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "SENKYC_DB_PASSWORD=overridden-by-service-connection",
            "senkyc.ai.internal-token=test-token",
            "management.endpoint.health.show-details=always"
        })
class SenkycApiApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    AiServiceClient aiServiceClient;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void stubAiService() {
        when(aiServiceClient.info()).thenReturn(new AiServiceInfo("senkyc-ai", "0.0.0-test", Map.of()));
    }

    @Test
    void healthIsUpAndIncludesDatabaseAndAiService() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"", "aiService", "db");
    }

    @Test
    void liquibaseCreatedTheInitialSchema() {
        var tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables).contains("tenant", "verification", "stored_object", "audit_event");
    }

    @Test
    void apiIsClosedByDefault() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/verifications", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void databaseRejectsUnknownVerificationStatus() {
        UUID tenant = insertTenant("status-check");

        assertThatThrownBy(() -> insertVerification(tenant, UUID.randomUUID(), "BOGUS"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsStoredObjectLinkedToAnotherTenantsVerification() {
        UUID tenantA = insertTenant("tenant-a");
        UUID tenantB = insertTenant("tenant-b");
        UUID verificationOfA = UUID.randomUUID();
        insertVerification(tenantA, verificationOfA, "PENDING");

        assertThatThrownBy(() -> jdbc.update(
                        "insert into stored_object (id, tenant_id, verification_id, kind, bucket, object_key,"
                                + " content_type, size_bytes, sha256, encryption_key_id, delete_after)"
                                + " values (?, ?, ?, 'SELFIE', 'b', 'k', 'image/jpeg', 10, ?, 'key-1', now())",
                        UUID.randomUUID(), tenantB, verificationOfA, "0".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertTenant(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tenant (id, code, name) values (?, ?, ?)", id, code + "-" + id, "Tenant fictif");
        return id;
    }

    private void insertVerification(UUID tenantId, UUID id, String status) {
        jdbc.update(
                "insert into verification (id, tenant_id, status, expires_at) values (?, ?, ?, ?)",
                id, tenantId, status, OffsetDateTime.now().plusHours(1));
    }
}
