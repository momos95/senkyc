package sn.senkyc.api.registry.application;

import java.util.Map;
import java.util.Optional;
import sn.senkyc.api.verification.domain.DocumentType;
import sn.senkyc.api.verification.domain.IssuingCountry;
import sn.senkyc.registry.spi.IdentityRegistryProvider;

/**
 * Décide, pour un pays et un type de document, si un contrôle d'intégrité officiel doit être tenté et par
 * quel connecteur. Toute absence (pays, document, droit du tenant) conduit à « pas de contrôle officiel » :
 * jamais à un contrôle improvisé ni à {@code VERIFIED_OFFICIAL}.
 */
public class RegistryRouter {

    public enum Reason {
        /** Un connecteur est configuré et le tenant y a droit. */
        OFFICIAL_CHECK,
        /** Le pays n'est pas activé : la vérification doit être refusée par l'appelant. */
        COUNTRY_NOT_ENABLED,
        /** Aucun connecteur pour ce document : KYC commun seulement. */
        NO_CONNECTOR_FOR_DOCUMENT,
        /** Le tenant n'a pas le droit au contrôle officiel (édition gratuite) : KYC commun seulement. */
        TENANT_NOT_ENTITLED
    }

    public record Route(Reason reason, IdentityRegistryProvider provider) {
        public boolean officialCheck() {
            return reason == Reason.OFFICIAL_CHECK;
        }
    }

    private final Map<IssuingCountry, Map<DocumentType, IdentityRegistryProvider>> connectors;

    /** @param connectors pays activés -> (type de document -> connecteur) ; un pays sans document a une map vide */
    public RegistryRouter(Map<IssuingCountry, Map<DocumentType, IdentityRegistryProvider>> connectors) {
        this.connectors = Map.copyOf(connectors);
    }

    public boolean isCountryEnabled(IssuingCountry country) {
        return connectors.containsKey(country);
    }

    public Route route(boolean tenantEntitled, IssuingCountry country, DocumentType documentType) {
        Map<DocumentType, IdentityRegistryProvider> byDocument = connectors.get(country);
        if (byDocument == null) {
            return new Route(Reason.COUNTRY_NOT_ENABLED, null);
        }
        IdentityRegistryProvider provider = byDocument.get(documentType);
        if (provider == null) {
            return new Route(Reason.NO_CONNECTOR_FOR_DOCUMENT, null);
        }
        if (!tenantEntitled) {
            return new Route(Reason.TENANT_NOT_ENTITLED, null);
        }
        return new Route(Reason.OFFICIAL_CHECK, provider);
    }

    /** Fournisseur configuré pour ce pays et ce document, indépendamment du droit du tenant. */
    public Optional<IdentityRegistryProvider> configuredConnector(IssuingCountry country, DocumentType type) {
        return Optional.ofNullable(connectors.getOrDefault(country, Map.of()).get(type));
    }
}
