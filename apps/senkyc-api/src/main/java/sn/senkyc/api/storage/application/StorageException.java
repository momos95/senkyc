package sn.senkyc.api.storage.application;

/** Échec technique du stockage. Le message ne contient jamais de contenu ni de secret. */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
