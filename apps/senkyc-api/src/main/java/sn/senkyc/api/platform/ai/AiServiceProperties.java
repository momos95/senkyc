package sn.senkyc.api.platform.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration du service IA. L'URL est fixée par l'exploitant : elle ne provient jamais d'une
 * entrée utilisateur (pas de vecteur SSRF).
 */
@Validated
@ConfigurationProperties(prefix = "senkyc.ai")
public record AiServiceProperties(
        @NotNull URI baseUrl,
        @NotBlank String internalToken,
        @NotNull @DefaultValue("2s") Duration connectTimeout,
        @NotNull @DefaultValue("5s") Duration readTimeout) {

    @Override
    public String toString() {
        return "AiServiceProperties[baseUrl=" + baseUrl + ", internalToken=<redacted>]";
    }
}
