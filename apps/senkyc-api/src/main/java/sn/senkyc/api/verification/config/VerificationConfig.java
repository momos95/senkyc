package sn.senkyc.api.verification.config;

import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sn.senkyc.api.platform.audit.AuditLog;
import sn.senkyc.api.registry.application.RegistryRouter;
import sn.senkyc.api.storage.application.ProtectedObjectStore;
import sn.senkyc.api.tenant.application.TenantService;
import sn.senkyc.api.verification.application.ImagePolicy;
import sn.senkyc.api.verification.application.ImageValidator;
import sn.senkyc.api.verification.application.StoredDocumentRepository;
import sn.senkyc.api.verification.application.VerificationRepository;
import sn.senkyc.api.verification.application.VerificationService;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VerificationProperties.class)
class VerificationConfig {

    @Bean
    ImageValidator imageValidator(VerificationProperties properties) {
        VerificationProperties.Documents d = properties.documents();
        return new ImageValidator(
                new ImagePolicy(d.maxBytes(), d.minBytes(), d.minShortSidePx(), d.maxPixels(), d.maxAspectRatio()));
    }

    /** Le stockage chiffré est facultatif (désactivé par défaut) : sans lui, l'envoi de documents répond 503. */
    @Bean
    VerificationService verificationService(
            VerificationRepository verifications,
            StoredDocumentRepository documents,
            TenantService tenants,
            RegistryRouter router,
            ObjectProvider<ProtectedObjectStore> objectStore,
            ImageValidator imageValidator,
            AuditLog audit,
            Clock clock,
            VerificationProperties properties) {
        return new VerificationService(
                verifications,
                documents,
                tenants,
                router,
                Optional.ofNullable(objectStore.getIfAvailable()),
                imageValidator,
                audit,
                clock,
                properties.ttl());
    }
}
