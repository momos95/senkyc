package sn.senkyc.api.verification.domain;

/** La clé d'idempotence a déjà servi pour une demande différente. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("La clé d'idempotence a déjà servi pour une demande différente.");
    }
}
