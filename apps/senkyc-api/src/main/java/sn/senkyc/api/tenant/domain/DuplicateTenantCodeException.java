package sn.senkyc.api.tenant.domain;

public class DuplicateTenantCodeException extends RuntimeException {

    public DuplicateTenantCodeException(String code) {
        super("A tenant with this code already exists: " + code);
    }
}
