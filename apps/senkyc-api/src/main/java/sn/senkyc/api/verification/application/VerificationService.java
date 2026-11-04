package sn.senkyc.api.verification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import sn.senkyc.api.platform.audit.AuditLog;
import sn.senkyc.api.platform.tenant.TenantContext;
import sn.senkyc.api.registry.application.RegistryRouter;
import sn.senkyc.api.storage.application.ProtectedObjectStore;
import sn.senkyc.api.storage.application.StoredObjectRef;
import sn.senkyc.api.tenant.application.TenantService;
import sn.senkyc.api.tenant.domain.Tenant;
import sn.senkyc.api.tenant.domain.TenantStatus;
import sn.senkyc.api.verification.application.ImageValidator.ValidatedImage;
import sn.senkyc.api.verification.domain.DocumentPart;
import sn.senkyc.api.verification.domain.DuplicateDocumentPartException;
import sn.senkyc.api.verification.domain.DocumentPartNotAllowedException;
import sn.senkyc.api.verification.domain.IdempotencyConflictException;
import sn.senkyc.api.verification.domain.StoredDocument;
import sn.senkyc.api.verification.domain.TenantSuspendedException;
import sn.senkyc.api.verification.domain.UnsupportedCountryException;
import sn.senkyc.api.verification.domain.Verification;
import sn.senkyc.api.verification.domain.VerificationNotFoundException;
import sn.senkyc.api.verification.domain.VerificationNotOpenException;
import sn.senkyc.api.verification.domain.VerificationStatus;

/** Création et lecture des vérifications, et dépôt (contrôlé et chiffré) des images. */
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    public record CreateResult(Verification verification, boolean replayed) {}

    public record VerificationView(Verification verification, List<StoredDocument> documents) {}

    private final VerificationRepository verifications;
    private final StoredDocumentRepository documents;
    private final TenantService tenants;
    private final RegistryRouter router;
    private final Optional<ProtectedObjectStore> objectStore;
    private final ImageValidator imageValidator;
    private final AuditLog audit;
    private final Clock clock;
    private final Duration timeToLive;

    public VerificationService(
            VerificationRepository verifications,
            StoredDocumentRepository documents,
            TenantService tenants,
            RegistryRouter router,
            Optional<ProtectedObjectStore> objectStore,
            ImageValidator imageValidator,
            AuditLog audit,
            Clock clock,
            Duration timeToLive) {
        this.verifications = verifications;
        this.documents = documents;
        this.tenants = tenants;
        this.router = router;
        this.objectStore = objectStore;
        this.imageValidator = imageValidator;
        this.audit = audit;
        this.clock = clock;
        this.timeToLive = timeToLive;
    }

    @Transactional
    public CreateResult create(CreateVerificationCommand command) {
        UUID tenantId = TenantContext.require();
        Tenant tenant = tenants.currentTenant();
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new TenantSuspendedException();
        }
        if (!router.isCountryEnabled(command.issuingCountry())) {
            throw new UnsupportedCountryException(command.issuingCountry());
        }
        if (command.idempotencyKey() != null) {
            Optional<Verification> existing = verifications.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                return replay(existing.get(), command);
            }
        }

        Instant now = Instant.now(clock);
        Verification verification = new Verification(
                UUID.randomUUID(),
                tenantId,
                command.externalReference(),
                command.idempotencyKey(),
                VerificationStatus.PENDING,
                command.documentType(),
                command.issuingCountry(),
                now.plus(timeToLive),
                now,
                0);
        if (!verifications.insert(verification)) {
            // Requête concurrente avec la même clé : on renvoie celle qui a gagné.
            Verification winner = verifications
                    .findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Idempotency key conflict without a row"));
            return replay(winner, command);
        }
        audit.record(
                "VERIFICATION_CREATED",
                "verification",
                verification.id().toString(),
                Map.of("documentType", verification.documentType().name(), "country", verification.issuingCountry().name()));
        return new CreateResult(verification, false);
    }

    private static CreateResult replay(Verification existing, CreateVerificationCommand command) {
        if (!existing.sameRequestAs(command.documentType(), command.issuingCountry(), command.externalReference())) {
            throw new IdempotencyConflictException();
        }
        return new CreateResult(existing, true);
    }

    @Transactional(readOnly = true)
    public VerificationView get(UUID id) {
        Verification verification = verifications.findById(id).orElseThrow(VerificationNotFoundException::new);
        return new VerificationView(verification, documents.findByVerification(id));
    }

    /**
     * Valide l'image (format, poids, dimensions), la chiffre, l'enregistre et la référence. Rien n'est écrit si
     * l'image est refusée ; si l'enregistrement des métadonnées échoue, l'objet déjà écrit est supprimé.
     */
    @Transactional
    public StoredDocument uploadDocument(UUID verificationId, DocumentPart part, byte[] content, String declaredType) {
        UUID tenantId = TenantContext.require();
        Verification verification =
                verifications.findById(verificationId).orElseThrow(VerificationNotFoundException::new);
        Instant now = Instant.now(clock);
        if (!verification.isOpenAt(now)) {
            throw new VerificationNotOpenException();
        }
        if (!part.isAllowedFor(verification.documentType())) {
            throw new DocumentPartNotAllowedException();
        }
        ProtectedObjectStore store = objectStore.orElseThrow(StorageNotConfiguredException::new);
        ValidatedImage image = imageValidator.validate(content, declaredType);
        if (documents.findByVerification(verificationId).stream().anyMatch(d -> d.part() == part)) {
            throw new DuplicateDocumentPartException();
        }

        Tenant tenant = tenants.currentTenant();
        StoredObjectRef ref = store.store(tenantId, verificationId, content);
        try {
            StoredDocument document = new StoredDocument(
                    UUID.randomUUID(),
                    tenantId,
                    verificationId,
                    part,
                    store.location(),
                    ref.objectKey(),
                    image.contentType(),
                    ref.sizeBytes(),
                    ref.sha256(),
                    ref.encryptionKeyId(),
                    now,
                    now.plus(Duration.ofDays(tenant.retentionDays())));
            if (!documents.insert(document)) {
                throw new DuplicateDocumentPartException();
            }
            audit.record(
                    "DOCUMENT_UPLOADED",
                    "verification",
                    verificationId.toString(),
                    Map.of("part", part.name(), "contentType", image.contentType(), "sizeBytes", ref.sizeBytes()));
            return document;
        } catch (RuntimeException e) {
            deleteQuietly(store, tenantId, ref.objectKey());
            throw e;
        }
    }

    private static void deleteQuietly(ProtectedObjectStore store, UUID tenantId, String objectKey) {
        try {
            store.delete(tenantId, objectKey);
        } catch (RuntimeException e) {
            log.warn("Could not remove an orphaned stored object after a failed upload");
        }
    }
}
