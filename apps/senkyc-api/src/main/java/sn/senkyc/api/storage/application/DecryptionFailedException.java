package sn.senkyc.api.storage.application;

/**
 * L'objet ne peut pas être déchiffré : données altérées, mauvais emplacement (AAD), clé inconnue ou
 * format invalide. Ne jamais traiter ce cas comme « objet valide » (fail-closed).
 */
public class DecryptionFailedException extends RuntimeException {

    public DecryptionFailedException(String message) {
        super(message);
    }

    public DecryptionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
