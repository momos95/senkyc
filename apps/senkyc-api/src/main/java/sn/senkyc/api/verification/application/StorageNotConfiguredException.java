package sn.senkyc.api.verification.application;

/** Le stockage chiffré est désactivé (senkyc.storage.enabled=false) : l'envoi de documents est impossible. */
public class StorageNotConfiguredException extends RuntimeException {

    public StorageNotConfiguredException() {
        super("Document storage is not configured");
    }
}
