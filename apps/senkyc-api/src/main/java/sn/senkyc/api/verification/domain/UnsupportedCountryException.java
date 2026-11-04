package sn.senkyc.api.verification.domain;

/** Le pays émetteur n'est pas activé dans la configuration (senkyc.countries). */
public class UnsupportedCountryException extends RuntimeException {

    public UnsupportedCountryException(IssuingCountry country) {
        super("Country not enabled: " + country);
    }
}
