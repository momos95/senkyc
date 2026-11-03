package sn.senkyc.api.platform.security;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/** Règles de validation d'un jeton d'accès : signature (ailleurs), dates, émetteur, audience, tenant. */
public final class SenkycJwtValidators {

    public static final String TENANT_CLAIM = "tenant_id";

    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private SenkycJwtValidators() {}

    public static OAuth2TokenValidator<Jwt> create(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                audienceValidator(audience),
                tenantValidator());
    }

    static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : failure("The required audience is missing");
    }

    /** Tout jeton doit porter un tenant_id valide, sauf un jeton d'administrateur de plateforme. */
    static OAuth2TokenValidator<Jwt> tenantValidator() {
        return jwt -> {
            if (realmRoles(jwt).contains(ApiPermissions.ADMIN_ROLE)) {
                return OAuth2TokenValidatorResult.success();
            }
            String tenant = jwt.getClaimAsString(TENANT_CLAIM);
            return tenant != null && UUID_FORMAT.matcher(tenant).matches()
                    ? OAuth2TokenValidatorResult.success()
                    : failure("A valid tenant_id claim is required");
        };
    }

    /** Rôles de realm Keycloak ({@code realm_access.roles}), lus sans présumer de la forme du claim. */
    static Set<String> realmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
