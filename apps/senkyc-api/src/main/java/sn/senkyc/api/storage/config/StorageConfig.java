package sn.senkyc.api.storage.config;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import sn.senkyc.api.storage.adapter.out.s3.S3ObjectStorage;
import sn.senkyc.api.storage.application.EncryptionKeyring;
import sn.senkyc.api.storage.application.ObjectCipher;
import sn.senkyc.api.storage.application.ObjectStorage;
import sn.senkyc.api.storage.application.ProtectedObjectStore;

/**
 * Câblage du stockage chiffré. Désactivé par défaut ({@code senkyc.storage.enabled=false}) : activé, un
 * serveur de stockage injoignable ou une clé invalide empêche le démarrage (fail-closed).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "senkyc.storage", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        return S3Client.builder()
                .endpointOverride(s3.endpoint())
                .region(Region.of(s3.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    ObjectStorage objectStorage(S3Client client, StorageProperties properties) {
        S3ObjectStorage storage = new S3ObjectStorage(client, properties.s3().bucket());
        storage.ensureBucket();
        return storage;
    }

    @Bean
    EncryptionKeyring encryptionKeyring(StorageProperties properties) {
        Map<String, byte[]> raw = new HashMap<>();
        properties.encryption().keys().forEach((id, base64) -> {
            try {
                raw.put(id, Base64.getDecoder().decode(base64.trim()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("La clé de chiffrement '" + id + "' n'est pas du base64 valide");
            }
        });
        return EncryptionKeyring.of(properties.encryption().currentKeyId(), raw);
    }

    @Bean
    ProtectedObjectStore protectedObjectStore(ObjectStorage storage, EncryptionKeyring keyring) {
        return new ProtectedObjectStore(storage, new ObjectCipher(keyring));
    }
}
