package sn.senkyc.api.platform.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Autorités = scopes OAuth2 ({@code SCOPE_x}, claim {@code scope}) + rôles de realm Keycloak
 * ({@code ROLE_x}, claim {@code realm_access.roles}).
 */
public final class SenkycJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        Collection<GrantedAuthority> scopes = scopeConverter.convert(jwt);
        if (scopes != null) {
            authorities.addAll(scopes);
        }
        for (String role : SenkycJwtValidators.realmRoles(jwt)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
