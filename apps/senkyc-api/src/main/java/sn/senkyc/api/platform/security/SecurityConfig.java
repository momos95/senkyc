package sn.senkyc.api.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import sn.senkyc.api.platform.tenant.TenantContextFilter;

/**
 * Resource server OAuth2 (JWT Keycloak), sans session, fail-closed : toute route non listée est refusée.
 * Le CSRF (attaque basée sur des cookies de session) ne s'applique pas à une API à jeton Bearer.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasAuthority("ROLE_" + ApiPermissions.ADMIN_ROLE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/verifications/**")
                        .hasAnyAuthority(ApiPermissions.authoritiesFor(ApiPermissions.VERIFICATIONS_READ))
                        .requestMatchers("/api/v1/verifications/**")
                        .hasAnyAuthority(ApiPermissions.authoritiesFor(ApiPermissions.VERIFICATIONS_WRITE))
                        .requestMatchers("/api/v1/tenant")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(new SenkycJwtAuthenticationConverter())))
                .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
