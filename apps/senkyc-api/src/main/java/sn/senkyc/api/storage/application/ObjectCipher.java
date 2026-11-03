package sn.senkyc.api.storage.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Chiffrement authentifié AES-256-GCM, IV aléatoire de 96 bits par objet.
 *
 * <p>Format : {@code [version=1][longueur keyId][keyId][IV 12 octets][chiffré + tag 16 octets]}.
 * Les données authentifiées additionnelles (AAD) sont la clé d'objet : un objet recopié sous une autre
 * clé (donc vers un autre tenant ou une autre vérification) ne se déchiffre pas.
 */
public final class ObjectCipher {

    private static final int VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final EncryptionKeyring keyring;
    private final SecureRandom random = new SecureRandom();

    public ObjectCipher(EncryptionKeyring keyring) {
        this.keyring = keyring;
    }

    public String currentKeyId() {
        return keyring.currentKeyId();
    }

    public byte[] encrypt(byte[] plaintext, String associatedData) {
        try {
            byte[] keyId = keyring.currentKeyId().getBytes(StandardCharsets.US_ASCII);
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keyring.current(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext);

            byte[] out = new byte[2 + keyId.length + IV_BYTES + encrypted.length];
            int pos = 0;
            out[pos++] = VERSION;
            out[pos++] = (byte) keyId.length;
            System.arraycopy(keyId, 0, out, pos, keyId.length);
            pos += keyId.length;
            System.arraycopy(iv, 0, out, pos, IV_BYTES);
            pos += IV_BYTES;
            System.arraycopy(encrypted, 0, out, pos, encrypted.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new StorageException("Échec du chiffrement", e);
        }
    }

    public byte[] decrypt(byte[] blob, String associatedData) {
        if (blob == null || blob.length < 2 + 1 + IV_BYTES + TAG_BITS / 8) {
            throw new DecryptionFailedException("Objet chiffré trop court");
        }
        if (blob[0] != VERSION) {
            throw new DecryptionFailedException("Version de format inconnue");
        }
        int keyIdLength = blob[1] & 0xFF;
        int ivStart = 2 + keyIdLength;
        if (keyIdLength == 0 || blob.length < ivStart + IV_BYTES + TAG_BITS / 8) {
            throw new DecryptionFailedException("Objet chiffré invalide");
        }
        String keyId = new String(blob, 2, keyIdLength, StandardCharsets.US_ASCII);
        SecretKey key = keyring.find(keyId);
        if (key == null) {
            throw new DecryptionFailedException("Clé de chiffrement inconnue");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(blob, ivStart, ivStart + IV_BYTES)));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(blob, ivStart + IV_BYTES, blob.length - ivStart - IV_BYTES);
        } catch (GeneralSecurityException e) {
            // Tag invalide : contenu altéré ou emplacement différent. Aucun détail n'est exposé.
            throw new DecryptionFailedException("Déchiffrement impossible", e);
        }
    }
}
