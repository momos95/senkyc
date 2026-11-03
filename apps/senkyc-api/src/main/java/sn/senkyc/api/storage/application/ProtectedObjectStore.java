package sn.senkyc.api.storage.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stocke et relit des objets sensibles (images d'identité) : chiffrement côté application avant l'envoi
 * au fournisseur, clés d'objet sans donnée personnelle, et contrôle de l'appartenance au tenant à la
 * lecture et à la suppression (défense en profondeur, en plus des contrôles de l'appelant).
 */
public class ProtectedObjectStore {

    /** Taille maximale d'un objet en clair (10 Mio) : évite de charger de gros contenus en mémoire. */
    public static final int MAX_PLAINTEXT_BYTES = 10 * 1024 * 1024;

    private final ObjectStorage storage;
    private final ObjectCipher cipher;

    public ProtectedObjectStore(ObjectStorage storage, ObjectCipher cipher) {
        this.storage = storage;
        this.cipher = cipher;
    }

    public StoredObjectRef store(UUID tenantId, UUID verificationId, byte[] plaintext) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(verificationId, "verificationId");
        Objects.requireNonNull(plaintext, "plaintext");
        if (plaintext.length == 0) {
            throw new IllegalArgumentException("Contenu vide");
        }
        if (plaintext.length > MAX_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException("Contenu trop volumineux");
        }
        String objectKey = "tenants/" + tenantId + "/verifications/" + verificationId + "/" + UUID.randomUUID();
        storage.put(objectKey, cipher.encrypt(plaintext, objectKey));
        return new StoredObjectRef(objectKey, sha256Hex(plaintext), plaintext.length, cipher.currentKeyId());
    }

    /**
     * @return le contenu en clair, ou vide si l'objet n'existe pas
     * @throws DecryptionFailedException si l'objet est altéré ou déplacé (jamais renvoyé comme valide)
     * @throws IllegalArgumentException si la clé n'appartient pas au tenant
     */
    public Optional<byte[]> read(UUID tenantId, String objectKey) {
        requireOwnedBy(tenantId, objectKey);
        return storage.get(objectKey).map(blob -> cipher.decrypt(blob, objectKey));
    }

    public void delete(UUID tenantId, String objectKey) {
        requireOwnedBy(tenantId, objectKey);
        storage.delete(objectKey);
    }

    private static void requireOwnedBy(UUID tenantId, String objectKey) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (objectKey == null || !objectKey.startsWith("tenants/" + tenantId + "/") || objectKey.contains("..")) {
            throw new IllegalArgumentException("Objet n'appartenant pas au tenant");
        }
    }

    static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
