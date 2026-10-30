package sn.senkyc.registry.spi;

/**
 * Point d'extension permettant de confirmer une identité auprès d'un registre officiel.
 *
 * <p>Une implémentation ne doit être branchée que si l'exploitant dispose d'une
 * <strong>autorisation officielle</strong> d'accès au registre concerné. SenKYC ne fournit
 * aucun accès à un registre d'État.
 *
 * <p>Contrat : une implémentation ne lève pas d'exception pour une indisponibilité du registre,
 * elle retourne {@link RegistryVerificationResult.Outcome#UNAVAILABLE}.
 */
public interface IdentityRegistryProvider {

    /** Identifiant stable du fournisseur (ex. {@code "noop"}, {@code "mock"}). */
    String providerId();

    /** Indique si ce fournisseur sait traiter le pays donné (code ISO 3166-1 alpha-2). */
    boolean supports(String countryCode);

    RegistryVerificationResult verify(RegistryVerificationRequest request);
}
