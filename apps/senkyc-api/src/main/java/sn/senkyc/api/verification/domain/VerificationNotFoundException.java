package sn.senkyc.api.verification.domain;

/** La vérification n'existe pas (ou n'appartient pas au tenant). */
public class VerificationNotFoundException extends RuntimeException {

    public VerificationNotFoundException() {
        super("La vérification n'existe pas (ou n'appartient pas au tenant).");
    }
}
