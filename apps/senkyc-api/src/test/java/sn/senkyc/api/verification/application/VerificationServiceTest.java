package sn.senkyc.api.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.senkyc.api.platform.audit.AuditLog;
import sn.senkyc.api.platform.tenant.TenantContext;
import sn.senkyc.api.registry.application.RegistryRouter;
import sn.senkyc.api.storage.application.EncryptionKeyring;
import sn.senkyc.api.storage.application.ObjectCipher;
import sn.senkyc.api.storage.application.ObjectStorage;
import sn.senkyc.api.storage.application.ProtectedObjectStore;
import sn.senkyc.api.tenant.application.TenantRepository;
import sn.senkyc.api.tenant.application.TenantService;
import sn.senkyc.api.tenant.domain.Tenant;
import sn.senkyc.api.tenant.domain.TenantStatus;
import sn.senkyc.api.verification.ImageFixtures;
import sn.senkyc.api.verification.application.InvalidImageException.Reason;
import sn.senkyc.api.verification.application.VerificationService.CreateResult;
import sn.senkyc.api.verification.domain.DocumentPart;
import sn.senkyc.api.verification.domain.DocumentPartNotAllowedException;
import sn.senkyc.api.verification.domain.DocumentType;
import sn.senkyc.api.verification.domain.DuplicateDocumentPartException;
import sn.senkyc.api.verification.domain.IdempotencyConflictException;
import sn.senkyc.api.verification.domain.IssuingCountry;
import sn.senkyc.api.verification.domain.StoredDocument;
import sn.senkyc.api.verification.domain.TenantSuspendedException;
import sn.senkyc.api.verification.domain.UnsupportedCountryException;
import sn.senkyc.api.verification.domain.Verification;
import sn.senkyc.api.verification.domain.VerificationNotFoundException;
import sn.senkyc.api.verification.domain.VerificationNotOpenException;
import sn.senkyc.api.verification.domain.VerificationStatus;

class VerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    // --- Doublures en mémoire ---------------------------------------------------------------------

    static class MemoryVerifications implements VerificationRepository {
        final Map<UUID, Verification> byId = new HashMap<>();

        @Override
        public Optional<Verification> findById(UUID id) {
            // Reproduit la RLS : seul le tenant courant voit ses lignes.
            return Optional.ofNullable(byId.get(id)).filter(v -> v.tenantId().equals(TenantContext.require()));
        }

        @Override
        public Optional<Verification> findByIdempotencyKey(String key) {
            return byId.values().stream()
                    .filter(v -> v.tenantId().equals(TenantContext.require()) && key.equals(v.idempotencyKey()))
                    .findFirst();
        }

        @Override
        public boolean insert(Verification v) {
            if (v.idempotencyKey() != null && findByIdempotencyKey(v.idempotencyKey()).isPresent()) {
                return false;
            }
            byId.put(v.id(), v);
            return true;
        }
    }

    static class MemoryDocuments implements StoredDocumentRepository {
        final List<StoredDocument> rows = new ArrayList<>();
        boolean failNextInsert;

        @Override
        public List<StoredDocument> findByVerification(UUID id) {
            return rows.stream().filter(d -> d.verificationId().equals(id)).toList();
        }

        @Override
        public boolean insert(StoredDocument d) {
            if (failNextInsert) {
                failNextInsert = false;
                throw new IllegalStateException("simulated database failure");
            }
            rows.add(d);
            return true;
        }
    }

    static class MemoryStorage implements ObjectStorage {
        final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public String location() {
            return "test-bucket";
        }

        @Override
        public void put(String key, byte[] content) {
            objects.put(key, content.clone());
        }

        @Override
        public Optional<byte[]> get(String key) {
            return Optional.ofNullable(objects.get(key));
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    static class MemoryTenants implements TenantRepository {
        final Map<UUID, Tenant> tenants = new HashMap<>();

        @Override
        public Optional<Tenant> findById(UUID id) {
            return Optional.ofNullable(tenants.get(id));
        }

        @Override
        public Tenant create(Tenant tenant) {
            tenants.put(tenant.id(), tenant);
            return tenant;
        }

        @Override
        public List<Tenant> findAll(long offset, int limit) {
            return List.copyOf(tenants.values());
        }

        @Override
        public long count() {
            return tenants.size();
        }
    }

    record AuditRecord(String action, String resourceId, Map<String, Object> details) {}

    private final MemoryVerifications verifications = new MemoryVerifications();
    private final MemoryDocuments documents = new MemoryDocuments();
    private final MemoryStorage storage = new MemoryStorage();
    private final MemoryTenants tenantRepository = new MemoryTenants();
    private final List<AuditRecord> audit = new ArrayList<>();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AuditLog auditLog = (action, type, id, details) -> audit.add(new AuditRecord(action, id, details));
    private final ProtectedObjectStore store = new ProtectedObjectStore(
            storage, new ObjectCipher(EncryptionKeyring.of("k1", Map.of("k1", new byte[EncryptionKeyring.KEY_BYTES]))));
    private final RegistryRouter router = new RegistryRouter(Map.of(IssuingCountry.SN, Map.of()));

    private VerificationService service(Optional<ProtectedObjectStore> objectStore) {
        return new VerificationService(
                verifications,
                documents,
                new TenantService(tenantRepository, clock),
                router,
                objectStore,
                new ImageValidator(ImagePolicy.STANDARD),
                auditLog,
                clock,
                Duration.ofHours(24));
    }

    private final VerificationService service = service(Optional.of(store));

    private void givenTenant(UUID id, TenantStatus status, int retentionDays) {
        tenantRepository.create(new Tenant(id, "t-" + id, "Tenant fictif", status, retentionDays, false, NOW));
    }

    private static CreateVerificationCommand command(DocumentType type, String key) {
        return new CreateVerificationCommand(type, IssuingCountry.SN, "ref-1", key);
    }

    private <T> T asA(java.util.function.Supplier<T> action) {
        return TenantContext.callAs(TENANT_A, action);
    }

    // --- Création ---------------------------------------------------------------------------------

    @Test
    void createsAPendingVerificationWithAnExpiry() {
        givenTenant(TENANT_A, TenantStatus.ACTIVE, 30);

        CreateResult result = asA(() -> service.create(command(DocumentType.NATIONAL_ID, null)));

        assertThat(result.replayed()).isFalse();
        Verification v = result.verification();
        assertThat(v.status()).isEqualTo(VerificationStatus.PENDING);
        assertThat(v.tenantId()).isEqualTo(TENANT_A);
        assertThat(v.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(audit).extracting(AuditRecord::action).containsExactly("VERIFICATION_CREATED");
    }

    @Test
    void sameIdempotencyKeyAndSameRequestReturnsTheSameVerification() {
        givenTenant(TENANT_A, TenantStatus.ACTIVE, 30);

        CreateResult first = asA(() -> service.create(command(DocumentType.NATIONAL_ID, "key-12345678")));
        CreateResult second = asA(() -> service.create(command(DocumentType.NATIONAL_ID, "key-12345678")));

        assertThat(second.replayed()).isTrue();
        assertThat(second.verification().id()).isEqualTo(first.verification().id());
        assertThat(verifications.byId).hasSize(1);
        assertThat(audit).hasSize(1);
    }

    @Test
    void sameIdempotencyKeyWithADifferentRequestIsRejected() {
        givenTenant(TENANT_A, TenantStatus.ACTIVE, 30);
        asA(() -> service.create(command(DocumentType.NATIONAL_ID, "key-12345678")));

        assertThatThrownBy(() -> asA(() -> service.create(command(DocumentType.PASSPORT, "key-12345678"))))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void idempotencyKeysAreScopedPerTenant() {
        givenTenant(TENANT_A, TenantStatus.ACTIVE, 30);
        givenTenant(TENANT_B, TenantStatus.ACTIVE, 30);

        CreateResult a = asA(() -> service.create(command(DocumentType.NATIONAL_ID, "shared-key-1")));
        CreateResult b = TenantContext.callAs(
                TENANT_B, () -> service.create(command(DocumentType.NATIONAL_ID, "shared-key-1")));

        assertThat(b.replayed()).isFalse();
        assertThat(b.verification().id()).isNotEqualTo(a.verification().id());
    }

    @Test
    void refusesACountryThatIsNotEnabled() {
        givenTenant(TENANT_A, TenantStatus.ACTIVE, 30);

        assertThatThrownBy(() -> asA(() -> service.create(
                        new CreateVerificationCommand(DocumentType.NATIONAL_ID, IssuingCountry.CI, null, null))))
                .isInstanceOf(UnsupportedCountryException.class);
        assertThat(verifications.byId).isEmpty();
    }

    @Test
    void refusesASuspendedTenant() {
        givenTenant(TENANT_A, TenantStatus.SUSPENDED, 30);

        assertThatThrownBy(() -> asA(() -> service.create(command(DocumentType.NATIONAL_ID, null))))
                .isInstanceOf(TenantSuspendedException.class);
    }

    // --- Lecture et isolation ---------------------------------------------------------------------

    @Test
    void anotherTenantCannotSeeAVerification() {
        givenTenant(TENANT_A, TenantStatus.ACTIVE, 30);
        givenTenant(TENANT_B, TenantStatus.ACTIVE, 30);
        UUID id = asA(() -> service.create(command(DocumentType.NATIONAL_ID, null))).verification().id();

        assertThatThrownBy(() -> TenantContext.callAs(TENANT_B, () -> service.get(id)))
                .isInstanceOf(VerificationNotFoundException.class);
        assertThat(asA(() -> service.get(id)).verification().id()).isEqualTo(id);
    }

    // --- Dépôt de documents -----------------------------------------------------------------------

    private UUID openVerification(DocumentType type) {
        givenTenant(TENANT_A, TenantStatus.ACTIVE, 30);
        return asA(() -> service.create(command(type, null))).verification().id();
    }

    @Test
    void storesAnEncryptedImageAndItsMetadata() {
        UUID id = openVerification(DocumentType.NATIONAL_ID);
        byte[] png = ImageFixtures.validPng();

        StoredDocument stored =
                asA(() -> service.uploadDocument(id, DocumentPart.DOCUMENT_FRONT, png, "image/png"));

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.bucket()).isEqualTo("test-bucket");
        assertThat(stored.objectKey()).startsWith("tenants/" + TENANT_A + "/verifications/" + id + "/");
        assertThat(stored.deleteAfter()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        byte[] raw = storage.objects.get(stored.objectKey());
        assertThat(raw).isNotEqualTo(png);
        assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain("\u0089PNG\r\n\u001a\n");
        assertThat(audit).extracting(AuditRecord::action).contains("DOCUMENT_UPLOADED");
        assertThat(audit.toString()).doesNotContain(stored.sha256());
    }

    @Test
    void rejectsAnInvalidImageWithoutStoringAnything() {
        UUID id = openVerification(DocumentType.NATIONAL_ID);

        assertThatThrownBy(() -> asA(() ->
                        service.uploadDocument(id, DocumentPart.DOCUMENT_FRONT, new byte[40_000], "image/png")))
                .isInstanceOfSatisfying(
                        InvalidImageException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.UNSUPPORTED_FORMAT));
        assertThat(storage.objects).isEmpty();
        assertThat(documents.rows).isEmpty();
    }

    @Test
    void aPartCanOnlyBeSubmittedOnce() {
        UUID id = openVerification(DocumentType.NATIONAL_ID);
        byte[] png = ImageFixtures.validPng();
        asA(() -> service.uploadDocument(id, DocumentPart.DOCUMENT_FRONT, png, null));

        assertThatThrownBy(() -> asA(() -> service.uploadDocument(id, DocumentPart.DOCUMENT_FRONT, png, null)))
                .isInstanceOf(DuplicateDocumentPartException.class);
        assertThat(storage.objects).hasSize(1);
    }

    @Test
    void aPassportHasNoBackSide() {
        UUID id = openVerification(DocumentType.PASSPORT);

        assertThatThrownBy(() -> asA(() ->
                        service.uploadDocument(id, DocumentPart.DOCUMENT_BACK, ImageFixtures.validPng(), null)))
                .isInstanceOf(DocumentPartNotAllowedException.class);
    }

    @Test
    void refusesDocumentsForAnotherTenantsVerification() {
        UUID id = openVerification(DocumentType.NATIONAL_ID);
        givenTenant(TENANT_B, TenantStatus.ACTIVE, 30);

        assertThatThrownBy(() -> TenantContext.callAs(
                        TENANT_B,
                        () -> service.uploadDocument(id, DocumentPart.SELFIE, ImageFixtures.validPng(), null)))
                .isInstanceOf(VerificationNotFoundException.class);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void refusesDocumentsOnceTheVerificationHasExpired() {
        UUID id = openVerification(DocumentType.NATIONAL_ID);
        Verification v = verifications.byId.get(id);
        verifications.byId.put(id, new Verification(
                v.id(), v.tenantId(), v.externalReference(), v.idempotencyKey(), v.status(), v.documentType(),
                v.issuingCountry(), NOW.minusSeconds(1), v.createdAt(), v.version()));

        assertThatThrownBy(() -> asA(() ->
                        service.uploadDocument(id, DocumentPart.SELFIE, ImageFixtures.validPng(), null)))
                .isInstanceOf(VerificationNotOpenException.class);
    }

    @Test
    void failsClearlyWhenStorageIsNotConfigured() {
        VerificationService withoutStorage = service(Optional.empty());
        givenTenant(TENANT_A, TenantStatus.ACTIVE, 30);
        UUID id = asA(() -> withoutStorage.create(command(DocumentType.NATIONAL_ID, null))).verification().id();

        assertThatThrownBy(() -> asA(() ->
                        withoutStorage.uploadDocument(id, DocumentPart.SELFIE, ImageFixtures.validPng(), null)))
                .isInstanceOf(StorageNotConfiguredException.class);
    }

    @Test
    void removesTheStoredObjectWhenTheMetadataCannotBeSaved() {
        UUID id = openVerification(DocumentType.NATIONAL_ID);
        documents.failNextInsert = true;

        assertThatThrownBy(() -> asA(() ->
                        service.uploadDocument(id, DocumentPart.SELFIE, ImageFixtures.validPng(), null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(storage.objects).isEmpty();
    }
}
