package sn.senkyc.api.verification.domain;

/**
 * États de l'UEMOA. Le périmètre est ouvert progressivement : seul le Sénégal est actif pour l'instant.
 * Activer un pays est une décision produit qui suppose d'avoir écrit et testé ses règles de documents.
 */
public enum IssuingCountry {
    SN(true),
    BJ(false),
    BF(false),
    CI(false),
    GW(false),
    ML(false),
    NE(false),
    TG(false);

    private final boolean enabled;

    IssuingCountry(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
