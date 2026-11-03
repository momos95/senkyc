package sn.senkyc.api.platform.security;

/** Permissions de l'API et rôles de plateforme. */
public final class ApiPermissions {

    public static final String VERIFICATIONS_READ = "kyc:verifications:read";
    public static final String VERIFICATIONS_WRITE = "kyc:verifications:write";
    /** Rôle de plateforme : administration des tenants. Un jeton admin n'a pas de tenant_id. */
    public static final String ADMIN_ROLE = "senkyc-admin";

    private ApiPermissions() {}

    /**
     * Une permission peut arriver comme scope OAuth2 ({@code SCOPE_x}) ou comme rôle de realm
     * ({@code ROLE_x}) : voir docs/adr/0004-authentification-keycloak.md.
     */
    public static String[] authoritiesFor(String permission) {
        return new String[] {"SCOPE_" + permission, "ROLE_" + permission};
    }
}
