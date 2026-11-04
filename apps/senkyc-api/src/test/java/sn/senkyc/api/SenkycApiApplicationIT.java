package sn.senkyc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sn.senkyc.api.platform.security.SenkycJwtValidators;
import sn.senkyc.api.platform.tenant.TenantContext;
import sn.senkyc.api.platform.ai.AiServiceClient;
import sn.senkyc.api.platform.ai.AiServiceInfo;

/**
 * Test d'intégration avec un vrai PostgreSQL (Testcontainers) : migrations Liquibase, rôle applicatif,
 * Row-Level Security, et chaîne complète jeton signé -> tenant -> base. Docker est requis ; son absence
 * fait échouer le test (jamais d'ignorance silencieuse). Le service IA est simulé.
 */
@Testcontainers
@Import(SenkycApiApplicationIT.JwtTestConfig.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "senkyc.ai.internal-token=test-token",
            "management.endpoint.health.show-details=always"
        })
class SenkycApiApplicationIT {

    private static final String ISSUER = "http://localhost:8081/realms/senkyc";
    private static final String AUDIENCE = "senkyc-api";
    private static final String APP_PASSWORD = "app-test-password";

    private static final RSAKey RSA_KEY = generateKey();

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine").withInitScript("db/test-init-roles.sql");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Compte applicatif pour l'application ; compte propriétaire (superuser du conteneur) pour Liquibase.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "senkyc_app");
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.liquibase.url", postgres::getJdbcUrl);
        registry.add("spring.liquibase.user", postgres::getUsername);
        registry.add("spring.liquibase.password", postgres::getPassword);
        registry.add("spring.liquibase.contexts", () -> "test");
        registry.add("senkyc.security.jwt.issuer", () -> ISSUER);
        registry.add("senkyc.security.jwt.audience", () -> AUDIENCE);
    }

    /** Remplace la récupération des clés Keycloak par une clé locale : le reste de la validation est celui de production. */
    @TestConfiguration
    static class JwtTestConfig {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() throws JOSEException {
            NimbusJwtDecoder decoder =
                    NimbusJwtDecoder.withPublicKey(RSA_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(SenkycJwtValidators.create(ISSUER, AUDIENCE));
            return decoder;
        }
    }

    @MockitoBean
    AiServiceClient aiServiceClient;

    @Autowired
    TestRestTemplate rest;

    /** DataSource applicatif (enveloppé par TenantAwareDataSource, rôle senkyc_app). */
    @Autowired
    DataSource appDataSource;

    private JdbcTemplate app;
    private JdbcTemplate owner;

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void setUp() {
        when(aiServiceClient.info()).thenReturn(new AiServiceInfo("senkyc-ai", "0.0.0-test", Map.of()));
        app = new JdbcTemplate(appDataSource);
        owner = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    // --- Schéma, santé, sécurité par défaut -------------------------------------------------------

    @Test
    void healthIsUpAndIncludesDatabaseAndAiService() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"", "aiService", "db");
    }

    @Test
    void liquibaseCreatedTheInitialSchema() {
        List<String> tables = owner.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables).contains("tenant", "verification", "stored_object", "audit_event");
    }

    @Test
    void devSeedIsNotAppliedOutsideTheDevContext() {
        assertThat(owner.queryForObject("select count(*) from tenant where code in ('demo-a', 'demo-b')", Long.class))
                .isZero();
    }

    @Test
    void apiIsClosedWithoutToken() {
        assertThat(rest.getForEntity("/api/v1/verifications", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/api/v1/tenant", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Contraintes de schéma (compte propriétaire) ----------------------------------------------

    @Test
    void databaseRejectsUnknownVerificationStatus() {
        UUID tenant = insertTenant("status-check");

        assertThatThrownBy(() -> insertVerification(tenant, UUID.randomUUID(), "BOGUS"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseAcceptsTheThreeDocumentTypesAndRejectsOthers() {
        UUID tenant = insertTenant("doc-types");

        insertVerificationWith(tenant, "NATIONAL_ID", "SN");
        insertVerificationWith(tenant, "PASSPORT", "SN");
        insertVerificationWith(tenant, "DRIVING_LICENSE", "SN");

        assertThatThrownBy(() -> insertVerificationWith(tenant, "VOTER_CARD", "SN"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseAcceptsUemoaCountriesAndRejectsOthers() {
        UUID tenant = insertTenant("countries");

        for (String country : List.of("SN", "BJ", "BF", "CI", "GW", "ML", "NE", "TG")) {
            insertVerificationWith(tenant, "NATIONAL_ID", country);
        }

        assertThatThrownBy(() -> insertVerificationWith(tenant, "NATIONAL_ID", "FR"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void officialVerificationIsDisabledByDefaultForNewTenants() {
        UUID tenant = insertTenant("free-edition");

        assertThat(owner.queryForObject(
                        "select official_verification_enabled from tenant where id = ?", Boolean.class, tenant))
                .isFalse();
    }

    @Test
    void databaseRejectsStoredObjectLinkedToAnotherTenantsVerification() {
        UUID tenantA = insertTenant("tenant-a");
        UUID tenantB = insertTenant("tenant-b");
        UUID verificationOfA = UUID.randomUUID();
        insertVerification(tenantA, verificationOfA, "PENDING");

        assertThatThrownBy(() -> owner.update(
                        "insert into stored_object (id, tenant_id, verification_id, kind, bucket, object_key,"
                                + " content_type, size_bytes, sha256, encryption_key_id, delete_after)"
                                + " values (?, ?, ?, 'SELFIE', 'b', 'k', 'image/jpeg', 10, ?, 'key-1', now())",
                        UUID.randomUUID(), tenantB, verificationOfA, "0".repeat(64)))
                .isInstanceOf(DataAccessException.class);
    }

    // --- Row-Level Security (compte applicatif) ---------------------------------------------------

    @Test
    void rowLevelSecurityShowsOnlyTheCurrentTenantsRows() {
        UUID tenantA = insertTenant("rls-a");
        UUID tenantB = insertTenant("rls-b");
        insertVerification(tenantA, UUID.randomUUID(), "PENDING");
        insertVerification(tenantB, UUID.randomUUID(), "PENDING");
        insertVerification(tenantB, UUID.randomUUID(), "FAILED");

        assertThat(countVerificationsAs(tenantA)).isEqualTo(1);
        assertThat(countVerificationsAs(tenantB)).isEqualTo(2);
    }

    @Test
    void withoutATenantNothingIsVisibleOrWritable() {
        UUID tenant = insertTenant("rls-none");
        insertVerification(tenant, UUID.randomUUID(), "PENDING");

        assertThat(app.queryForObject("select count(*) from verification", Long.class)).isZero();
        assertThatThrownBy(() -> app.update(
                        "insert into verification (id, tenant_id, status, expires_at, document_type, issuing_country)"
                                + " values (?, ?, 'PENDING', ?, 'NATIONAL_ID', 'SN')",
                        UUID.randomUUID(), tenant, OffsetDateTime.now().plusHours(1)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void aTenantCannotWriteRowsForAnotherTenant() {
        UUID tenantA = insertTenant("write-a");
        UUID tenantB = insertTenant("write-b");

        assertThatThrownBy(() -> TenantContext.runAs(tenantA, () -> app.update(
                        "insert into verification (id, tenant_id, status, expires_at, document_type, issuing_country)"
                                + " values (?, ?, 'PENDING', ?, 'NATIONAL_ID', 'SN')",
                        UUID.randomUUID(), tenantB, OffsetDateTime.now().plusHours(1))))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void aTenantCannotUpdateOrDeleteAnotherTenantsRows() {
        UUID tenantA = insertTenant("upd-a");
        UUID tenantB = insertTenant("upd-b");
        UUID verificationOfB = UUID.randomUUID();
        insertVerification(tenantB, verificationOfB, "PENDING");

        int updated = TenantContext.callAs(tenantA, () -> app.update(
                "update verification set status = 'CANCELLED' where id = ?", verificationOfB));
        int deleted = TenantContext.callAs(
                tenantA, () -> app.update("delete from verification where id = ?", verificationOfB));

        assertThat(updated).isZero();
        assertThat(deleted).isZero();
        assertThat(owner.queryForObject("select status from verification where id = ?", String.class, verificationOfB))
                .isEqualTo("PENDING");
    }

    @Test
    void theAuditLogIsAppendOnlyForTheApplicationRole() {
        UUID tenant = insertTenant("audit");

        TenantContext.runAs(tenant, () -> app.update(
                "insert into audit_event (tenant_id, actor, action) values (?, 'tester', 'TEST')", tenant));

        assertThatThrownBy(() -> TenantContext.runAs(tenant, () -> app.update("delete from audit_event")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> TenantContext.runAs(tenant, () -> app.update("update audit_event set actor = 'x'")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void theApplicationRoleCannotChangeTheSchema() {
        assertThatThrownBy(() -> app.execute("create table must_not_exist (id int)"))
                .isInstanceOf(DataAccessException.class);
    }

    // --- Chaîne complète : jeton signé -> validation -> tenant -> base ----------------------------

    @Test
    void aTenantTokenGetsItsOwnTenant() throws Exception {
        UUID tenant = insertTenant("e2e-a");
        insertTenant("e2e-b");

        ResponseEntity<String> response = get("/api/v1/tenant", token(tenant.toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(tenant.toString()).contains("e2e-a");
    }

    @Test
    void invalidTokensAreRejected() throws Exception {
        UUID tenant = insertTenant("e2e-bad");

        assertThat(get("/api/v1/tenant", token(tenant.toString(), "wrong-audience", -1)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/tenant", token(tenant.toString(), AUDIENCE, -10_000)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/tenant", token(null)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminEndpointsRequireThePlatformAdminRole() throws Exception {
        UUID tenant = insertTenant("e2e-admin");

        assertThat(get("/api/v1/admin/tenants", token(tenant.toString())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> asAdmin = get("/api/v1/admin/tenants", token(null, "senkyc-admin"));
        assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asAdmin.getBody()).contains("e2e-admin");
    }

    @Test
    void anAdminTokenHasNoTenantSoTheTenantEndpointIsForbidden() throws Exception {
        assertThat(get("/api/v1/tenant", token(null, "senkyc-admin")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- Utilitaires ------------------------------------------------------------------------------

    private long countVerificationsAs(UUID tenant) {
        return TenantContext.callAs(tenant, () -> app.queryForObject("select count(*) from verification", Long.class));
    }

    private UUID insertTenant(String code) {
        UUID id = UUID.randomUUID();
        owner.update("insert into tenant (id, code, name) values (?, ?, ?)", id, code + "-" + id, "Tenant fictif");
        return id;
    }

    private void insertVerification(UUID tenantId, UUID id, String status) {
        owner.update(
                "insert into verification (id, tenant_id, status, expires_at, document_type, issuing_country)"
                        + " values (?, ?, ?, ?, 'NATIONAL_ID', 'SN')",
                id, tenantId, status, OffsetDateTime.now().plusHours(1));
    }

    private void insertVerificationWith(UUID tenantId, String documentType, String country) {
        owner.update(
                "insert into verification (id, tenant_id, status, expires_at, document_type, issuing_country)"
                        + " values (?, ?, 'PENDING', ?, ?, ?)",
                UUID.randomUUID(), tenantId, OffsetDateTime.now().plusHours(1), documentType, country);
    }

    private ResponseEntity<String> get(String path, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static String token(String tenantId, String... realmRoles) throws JOSEException {
        return token(tenantId, AUDIENCE, 300, realmRoles);
    }

    private static String token(String tenantId, String audience, long validForSeconds, String... realmRoles)
            throws JOSEException {
        long now = System.currentTimeMillis();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("partner-test")
                .audience(audience)
                .issueTime(new Date(now - 20_000_000))
                .expirationTime(new Date(now + validForSeconds * 1000))
                .claim("realm_access", Map.of("roles", List.of(realmRoles)));
        if (tenantId != null) {
            claims.claim("tenant_id", tenantId);
        }
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(RSA_KEY));
        return jwt.serialize();
    }

    private static String token(String tenantId, String audience, int validForSeconds) throws JOSEException {
        return token(tenantId, audience, (long) validForSeconds, new String[0]);
    }
}
