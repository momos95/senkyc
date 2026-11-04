package sn.senkyc.api.verification.domain;

/** La vérification n'accepte plus de documents (statut ou expiration). */
public class VerificationNotOpenException extends RuntimeException {

    public VerificationNotOpenException() {
        super("La vérification n'accepte plus de documents (statut ou expiration).");
    }
}
