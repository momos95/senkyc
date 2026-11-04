package sn.senkyc.api.verification.domain;

/** Cette partie n'existe pas pour ce type de document. */
public class DocumentPartNotAllowedException extends RuntimeException {

    public DocumentPartNotAllowedException() {
        super("Cette partie n'existe pas pour ce type de document.");
    }
}
