package sn.senkyc.api.storage.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration du stockage. Chargée uniquement si {@code senkyc.storage.enabled=true}.
 * Aucun secret n'a de valeur par défaut, et {@code toString()} ne les affiche jamais.
 */
@Validated
@ConfigurationProperties(prefix = "senkyc.storage")
public record StorageProperties(@Valid @NotNull S3 s3, @Valid @NotNull Encryption encryption) {

    public record S3(
            @NotNull URI endpoint,
            @NotBlank String region,
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9.-]{2,62}") String bucket,
            @NotBlank String accessKey,
            @NotBlank String secretKey) {

        @Override
        public String toString() {
            return "S3[endpoint=" + endpoint + ", region=" + region + ", bucket=" + bucket + ", credentials=***]";
        }
    }

    /**
     * @param currentKeyId identifiant de la clé courante ([a-z0-9], 1 à 32 caractères)
     * @param keys clés AES-256 de 32 octets encodées en base64, indexées par identifiant
     */
    public record Encryption(@NotBlank String currentKeyId, @NotNull Map<String, String> keys) {

        @Override
        public String toString() {
            return "Encryption[currentKeyId=" + currentKeyId + ", keys=" + keys.size() + "]";
        }
    }
}
