package sn.senkyc.api.verification.domain;

/**
 * États de l'UEMOA. Un pays n'est actif que s'il est configuré dans {@code senkyc.countries} (voir ADR 0010) ;
 * l'ouvrir suppose d'avoir écrit et testé ses règles de documents.
 */
public enum IssuingCountry {
    SN,
    BJ,
    BF,
    CI,
    GW,
    ML,
    NE,
    TG
}
