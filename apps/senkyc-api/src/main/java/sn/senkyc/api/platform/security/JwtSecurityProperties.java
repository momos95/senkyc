package sn.senkyc.api.platform.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validation des jetons Keycloak.
 *
 * <p>{@code issuer} est la valeur attendue du claim {@code iss} (URL publique de Keycloak) ;
 * {@code jwkSetUri} est l'URL, éventuellement interne au réseau Docker, où récupérer les clés
 * de signature. Les deux sont distincts volontairement.
 */
@Validated
@ConfigurationProperties(prefix = "senkyc.security.jwt")
public record JwtSecurityProperties(
        @NotBlank String issuer, @NotNull URI jwkSetUri, @NotBlank String audience) {}
