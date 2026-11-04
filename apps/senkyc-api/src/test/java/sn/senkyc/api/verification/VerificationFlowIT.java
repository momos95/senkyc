package sn.senkyc.api.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sn.senkyc.api.platform.ai.AiServiceClient;
import sn.senkyc.api.platform.ai.AiServiceInfo;
import sn.senkyc.api.platform.security.SenkycJwtValidators;
import sn.senkyc.api.storage.application.ObjectStorage;

/**
 * Parcours complet contre de vrais services (PostgreSQL + RustFS) : jeton signé, création idempotente, envoi
 * chiffré d'images, isolation entre tenants, journal d'audit. Docker est requis (échec sinon). Les données et
 * identifiants sont fictifs et propres aux conteneurs éphémères.
 */
@Testcontainers
@Import(VerificationFlowIT.JwtTestConfig.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"senkyc.ai.internal-token=test-token", "senkyc.storage.enabled=true"})
class VerificationFlowIT {

    private static final String ISSUER = "http://localhost:8081/realms/senkyc";
    private static final String AUDIENCE = "senkyc-api";
    private static final String S3_ACCESS_KEY = "it-access-key";
    private static final String S3_SECRET_KEY = "it-secret-key-not-a-real-secret";
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final RSAKey RSA_KEY = generateKey();

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine").withInitScript("db/test-init-roles.sql");

    @Container
    static GenericContainer<?> rustfs = new GenericContainer<>(
                    "rustfs/rustfs@sha256:ed9210d6411d77ffceb70d94b964a3cb7d225983368bc25bebe01e995293dcc2")
            .withEnv("RUSTFS_ACCESS_KEY", S3_ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", S3_SECRET_KEY)
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(90)));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "senkyc_app");
        registry.add("spring.datasource.password", () -> "app-test-password");
        registry.add("spring.liquibase.url", postgres::getJdbcUrl);
        registry.add("spring.liquibase.user", postgres::getUsername);
        registry.add("spring.liquibase.password", postgres::getPassword);
        registry.add("spring.liquibase.contexts", () -> "test");
        registry.add("senkyc.security.jwt.issuer", () -> ISSUER);
        registry.add("senkyc.security.jwt.audience", () -> AUDIENCE);
        registry.add("senkyc.storage.s3.endpoint", () -> "http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000));
        registry.add("senkyc.storage.s3.access-key", () -> S3_ACCESS_KEY);
        registry.add("senkyc.storage.s3.secret-key", () -> S3_SECRET_KEY);
        registry.add("senkyc.storage.s3.bucket", () -> "senkyc-flow-it");
        registry.add("senkyc.storage.encryption.current-key-id", () -> "k1");
        registry.add("senkyc.storage.encryption.keys.k1", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

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

    @Autowired
    ObjectStorage objectStorage;

    private JdbcTemplate owner;

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("flow").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void setUp() {
        when(aiServiceClient.info()).thenReturn(new AiServiceInfo("senkyc-ai", "0.0.0-test", Map.of()));
        owner = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    // --- Utilitaires ------------------------------------------------------------------------------

    private UUID insertTenant(String code) {
        UUID id = UUID.randomUUID();
        owner.update("insert into tenant (id, code, name) values (?, ?, ?)", id, code + "-" + id, "Tenant fictif");
        return id;
    }

    private static String token(UUID tenant, String... roles) {
        try {
            long now = System.currentTimeMillis();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject("partner-test")
                    .audience(AUDIENCE)
                    .issueTime(new Date(now - 10_000))
                    .expirationTime(new Date(now + 300_000))
                    .claim("tenant_id", tenant.toString())
                    .claim("realm_access", Map.of("roles", List.of(roles)))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(RSA_KEY));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String writer(UUID tenant) {
        return token(tenant, "kyc:verifications:write", "kyc:verifications:read");
    }

    private ResponseEntity<String> postJson(String bearer, String idempotencyKey, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return rest.exchange("/api/v1/verifications", HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> getVerification(String bearer, String id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return rest.exchange("/api/v1/verifications/" + id, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> upload(String bearer, String id, String part, byte[] content, String contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("part", part);
        builder.part("file", new ByteArrayResource(content)).filename("image").contentType(MediaType.parseMediaType(contentType));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(
                URI.create(rest.getRootUri() + "/api/v1/verifications/" + id + "/documents"),
                HttpMethod.POST,
                new HttpEntity<>(builder.build(), headers),
                String.class);
    }

    private static String idOf(ResponseEntity<String> response) {
        String body = response.getBody();
        int start = body.indexOf("\"id\":\"") + 6;
        return body.substring(start, start + 36);
    }

    private static final String NATIONAL_ID_SN = "{\"documentType\":\"NATIONAL_ID\",\"issuingCountry\":\"SN\"}";

    // --- Scénarios --------------------------------------------------------------------------------

    @Test
    void fullFlowCreateUploadReadWithEncryptionAndAudit() {
        UUID tenant = insertTenant("flow");
        String bearer = writer(tenant);

        ResponseEntity<String> created = postJson(bearer, null, NATIONAL_ID_SN);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("\"status\":\"PENDING\"").doesNotContain(tenant.toString());
        String id = idOf(created);

        ResponseEntity<String> front = upload(bearer, id, "DOCUMENT_FRONT", ImageFixtures.validPng(), "image/png");
        assertThat(front.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> selfie = upload(bearer, id, "SELFIE", ImageFixtures.validJpeg(), "image/jpeg");
        assertThat(selfie.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> read = getVerification(bearer, id);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody()).contains("DOCUMENT_FRONT", "SELFIE").doesNotContain("objectKey", "sha256");

        // Métadonnées en base, objet chiffré dans le stockage, conservation selon la rétention du tenant (30 j).
        Map<String, Object> row = owner.queryForMap(
                "select object_key, bucket, encryption_key_id, size_bytes, delete_after from stored_object"
                        + " where verification_id = ? and kind = 'DOCUMENT_FRONT'",
                UUID.fromString(id));
        String objectKey = (String) row.get("object_key");
        assertThat(objectKey).startsWith("tenants/" + tenant + "/verifications/" + id + "/");
        assertThat(row.get("encryption_key_id")).isEqualTo("k1");
        assertThat(row.get("bucket")).isEqualTo("senkyc-flow-it");
        OffsetDateTime deleteAfter = ((java.sql.Timestamp) row.get("delete_after")).toInstant().atOffset(java.time.ZoneOffset.UTC);
        assertThat(deleteAfter).isAfter(OffsetDateTime.now().plusDays(29)).isBefore(OffsetDateTime.now().plusDays(31));
        byte[] stored = objectStorage.get(objectKey).orElseThrow();
        assertThat(java.util.Arrays.copyOf(stored, PNG_MAGIC.length)).isNotEqualTo(PNG_MAGIC);
        assertThat(stored.length).isGreaterThan(((Number) row.get("size_bytes")).intValue());

        assertThat(owner.queryForList(
                        "select action from audit_event where tenant_id = ? order by id", String.class, tenant))
                .containsExactly("VERIFICATION_CREATED", "DOCUMENT_UPLOADED", "DOCUMENT_UPLOADED");
        assertThat(owner.queryForObject(
                        "select count(*) from audit_event where tenant_id = ? and details::text like '%sha%'",
                        Long.class, tenant))
                .isZero();
    }

    @Test
    void idempotencyReplaysAndConflicts() {
        UUID tenant = insertTenant("idem");
        String bearer = writer(tenant);

        ResponseEntity<String> first = postJson(bearer, "key-abcdefgh", NATIONAL_ID_SN);
        ResponseEntity<String> replay = postJson(bearer, "key-abcdefgh", NATIONAL_ID_SN);
        ResponseEntity<String> conflict =
                postJson(bearer, "key-abcdefgh", "{\"documentType\":\"PASSPORT\",\"issuingCountry\":\"SN\"}");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(idOf(replay)).isEqualTo(idOf(first));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(owner.queryForObject("select count(*) from verification where tenant_id = ?", Long.class, tenant))
                .isEqualTo(1L);
    }

    @Test
    void anotherTenantCannotReadOrUploadToAVerification() {
        UUID a = insertTenant("iso-a");
        UUID b = insertTenant("iso-b");
        String id = idOf(postJson(writer(a), null, NATIONAL_ID_SN));

        assertThat(getVerification(writer(b), id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(upload(writer(b), id, "SELFIE", ImageFixtures.validPng(), "image/png").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidImagesAndPartsAreRefusedAndNothingIsStored() {
        UUID tenant = insertTenant("refuse");
        String bearer = writer(tenant);
        String id = idOf(postJson(bearer, null, NATIONAL_ID_SN));
        byte[] gif = new byte[30_000];
        System.arraycopy("GIF89a".getBytes(StandardCharsets.US_ASCII), 0, gif, 0, 6);

        ResponseEntity<String> notAnImage = upload(bearer, id, "DOCUMENT_FRONT", gif, "image/png");
        ResponseEntity<String> tiny = upload(bearer, id, "DOCUMENT_FRONT", new byte[10], "image/png");
        ResponseEntity<String> mismatch = upload(bearer, id, "DOCUMENT_FRONT", ImageFixtures.validPng(), "image/jpeg");
        ResponseEntity<String> badPart = upload(bearer, id, "NOT_A_PART", ImageFixtures.validPng(), "image/png");

        assertThat(notAnImage.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(notAnImage.getBody()).contains("UNSUPPORTED_FORMAT");
        assertThat(tiny.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(tiny.getBody()).contains("TOO_SMALL");
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mismatch.getBody()).contains("CONTENT_TYPE_MISMATCH");
        assertThat(badPart.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(owner.queryForObject(
                        "select count(*) from stored_object where verification_id = ?", Long.class, UUID.fromString(id)))
                .isZero();
    }

    @Test
    void aPartCanBeSubmittedOnlyOnceAndAPassportHasNoBack() {
        UUID tenant = insertTenant("parts");
        String bearer = writer(tenant);
        String id = idOf(postJson(bearer, null, NATIONAL_ID_SN));
        String passport = idOf(postJson(bearer, null, "{\"documentType\":\"PASSPORT\",\"issuingCountry\":\"SN\"}"));

        assertThat(upload(bearer, id, "DOCUMENT_BACK", ImageFixtures.validPng(), "image/png").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(upload(bearer, id, "DOCUMENT_BACK", ImageFixtures.validPng(), "image/png").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(upload(bearer, passport, "DOCUMENT_BACK", ImageFixtures.validPng(), "image/png").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void countriesNotEnabledAndMissingPermissionsAreRefused() {
        UUID tenant = insertTenant("authz");

        assertThat(postJson(writer(tenant), null, "{\"documentType\":\"NATIONAL_ID\",\"issuingCountry\":\"CI\"}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(postJson(token(tenant, "kyc:verifications:read"), null, NATIONAL_ID_SN).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.postForEntity("/api/v1/verifications", new HttpEntity<>(NATIONAL_ID_SN), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
