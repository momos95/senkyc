package sn.senkyc.api.platform.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Établit le {@link TenantContext} depuis le claim {@code tenant_id} du jeton déjà validé, puis le
 * nettoie. Volontairement NON déclaré comme bean : il est inséré dans la chaîne de sécurité
 * (après l'authentification Bearer) par {@code SecurityConfig}.
 */
public final class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_CLAIM = "tenant_id";

    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                String claim = jwtAuthentication.getToken().getClaimAsString(TENANT_CLAIM);
                if (claim != null && UUID_FORMAT.matcher(claim).matches()) {
                    TenantContext.set(UUID.fromString(claim));
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
