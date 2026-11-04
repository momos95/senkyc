package sn.senkyc.api.verification.application;

/** Image refusée. Le code {@link Reason} est renvoyé au client ; aucun contenu de l'image ne l'est. */
public class InvalidImageException extends RuntimeException {

    public enum Reason {
        EMPTY,
        TOO_SMALL,
        TOO_LARGE,
        UNSUPPORTED_FORMAT,
        CONTENT_TYPE_MISMATCH,
        CORRUPTED,
        DIMENSIONS_TOO_SMALL,
        DIMENSIONS_TOO_LARGE,
        ASPECT_RATIO
    }

    private final Reason reason;

    public InvalidImageException(Reason reason) {
        super("Invalid image: " + reason);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
