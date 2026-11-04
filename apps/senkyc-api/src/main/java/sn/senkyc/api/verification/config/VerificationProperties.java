package sn.senkyc.api.verification.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Réglages des vérifications. Les images sont limitées à 8 Mio par défaut : garder cette valeur inférieure ou
 * égale à {@code spring.servlet.multipart.max-file-size}, sinon la limite du serveur s'applique en premier.
 */
@Validated
@ConfigurationProperties(prefix = "senkyc.verification")
public record VerificationProperties(
        @DefaultValue("PT24H") @NotNull Duration ttl, @DefaultValue @Valid @NotNull Documents documents) {

    public record Documents(
            @DefaultValue("8388608") @Min(1024) int maxBytes,
            @DefaultValue("20480") @Min(1) int minBytes,
            @DefaultValue("480") @Min(1) int minShortSidePx,
            @DefaultValue("40000000") @Min(1) long maxPixels,
            @DefaultValue("4.0") @DecimalMin("1.0") double maxAspectRatio) {}
}
