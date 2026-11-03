package sn.senkyc.api.platform.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtSecurityProperties.class)
class JwtConfig {

    /** Les clés sont récupérées à la première validation : l'API démarre même si Keycloak est absent. */
    @Bean
    JwtDecoder jwtDecoder(JwtSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(SenkycJwtValidators.create(properties.issuer(), properties.audience()));
        return decoder;
    }
}
