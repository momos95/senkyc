package sn.senkyc.api.verification.domain;

/** Le tenant est suspendu. */
public class TenantSuspendedException extends RuntimeException {

    public TenantSuspendedException() {
        super("Le tenant est suspendu.");
    }
}
