package sn.senkyc.api.registry.config;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sn.senkyc.api.registry.application.RegistryRouter;
import sn.senkyc.api.verification.domain.DocumentType;
import sn.senkyc.api.verification.domain.IssuingCountry;
import sn.senkyc.registry.spi.IdentityRegistryProvider;

/**
 * Construit le routeur et valide la configuration au démarrage (fail-fast) : tout connecteur référencé doit
 * exister, être unique et déclarer qu'il gère le pays. Aucun connecteur n'est fourni par défaut.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CountryProperties.class)
class RegistryRoutingConfig {

    @Bean
    RegistryRouter registryRouter(CountryProperties properties, List<IdentityRegistryProvider> providers) {
        Map<String, IdentityRegistryProvider> byId = new HashMap<>();
        for (IdentityRegistryProvider provider : providers) {
            if (byId.put(provider.providerId(), provider) != null) {
                throw new IllegalStateException("Deux connecteurs portent le même identifiant : " + provider.providerId());
            }
        }

        Map<IssuingCountry, Map<DocumentType, IdentityRegistryProvider>> routing = new EnumMap<>(IssuingCountry.class);
        properties.countries().forEach((country, config) -> {
            if (!config.enabled()) {
                return;
            }
            Map<DocumentType, IdentityRegistryProvider> byDocument = new EnumMap<>(DocumentType.class);
            config.documents().forEach((type, document) -> {
                String id = document == null ? null : document.connector();
                if (id == null || id.isBlank()) {
                    throw new IllegalStateException(
                            "Connecteur manquant pour " + country + "/" + type + " : retirer l'entrée pour n'avoir"
                                    + " aucun contrôle d'intégrité");
                }
                IdentityRegistryProvider provider = byId.get(id);
                if (provider == null) {
                    throw new IllegalStateException("Connecteur inconnu '" + id + "' pour " + country + "/" + type);
                }
                if (!provider.supports(country.name())) {
                    throw new IllegalStateException(
                            "Le connecteur '" + id + "' ne gère pas le pays " + country + " (" + type + ")");
                }
                byDocument.put(type, provider);
            });
            routing.put(country, byDocument);
        });
        return new RegistryRouter(routing);
    }
}
