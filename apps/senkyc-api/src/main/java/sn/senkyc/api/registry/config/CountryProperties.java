package sn.senkyc.api.registry.config;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import sn.senkyc.api.verification.domain.DocumentType;
import sn.senkyc.api.verification.domain.IssuingCountry;

/**
 * Pays activés et connecteur de vérification d'intégrité par type de document.
 *
 * <p>Les clés sont des énumérations : un pays ou un type de document inconnu (faute de frappe) fait échouer
 * le démarrage. Un pays dont {@code enabled} est faux est considéré comme absent.
 */
@Validated
@ConfigurationProperties(prefix = "senkyc")
public record CountryProperties(@NotNull Map<IssuingCountry, Country> countries) {

    public CountryProperties {
        countries = countries == null ? Map.of() : countries;
    }

    /** @param documents type de document -> identifiant du connecteur ; vide = pas de contrôle d'intégrité */
    public record Country(boolean enabled, Map<DocumentType, Document> documents) {
        public Country {
            documents = documents == null ? Map.of() : documents;
        }
    }

    public record Document(String connector) {}
}
