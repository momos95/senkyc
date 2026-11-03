package sn.senkyc.api.platform.tenant;

/** Une opération exige un tenant alors que le jeton n'en porte pas (ex. jeton d'administration). */
public class TenantContextMissingException extends RuntimeException {

    public TenantContextMissingException() {
        super("No tenant in the current context");
    }
}
