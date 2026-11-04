package sn.senkyc.api.verification.domain;

/** Cette partie de document a déjà été soumise. */
public class DuplicateDocumentPartException extends RuntimeException {

    public DuplicateDocumentPartException() {
        super("Cette partie de document a déjà été soumise.");
    }
}
