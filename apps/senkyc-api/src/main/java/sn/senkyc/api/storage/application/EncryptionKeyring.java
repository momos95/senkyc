package sn.senkyc.api.storage.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Trousseau de clés AES-256. Une clé est identifiée par un {@code keyId} ([a-z0-9], 1 à 32 caractères),
 * écrit dans chaque objet chiffré : on peut donc introduire une nouvelle clé courante tout en
 * gardant les anciennes pour relire les objets existants (rotation).
 *
 * <p>Les clés ne sont jamais affichées : {@link #toString()} ne les révèle pas.
 */
public final class EncryptionKeyring {

    public static final int KEY_BYTES = 32;
    static final Pattern KEY_ID = Pattern.compile("[a-z0-9]{1,32}");

    private final String currentKeyId;
    private final Map<String, SecretKey> keys;

    private EncryptionKeyring(String currentKeyId, Map<String, SecretKey> keys) {
        this.currentKeyId = currentKeyId;
        this.keys = keys;
    }

    /** @param rawKeys clés brutes de 32 octets indexées par keyId (les tableaux sont copiés) */
    public static EncryptionKeyring of(String currentKeyId, Map<String, byte[]> rawKeys) {
        Objects.requireNonNull(currentKeyId, "currentKeyId");
        Objects.requireNonNull(rawKeys, "rawKeys");
        Map<String, SecretKey> copy = new HashMap<>();
        rawKeys.forEach((id, raw) -> {
            if (id == null || !KEY_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("Identifiant de clé invalide : attendu [a-z0-9]{1,32}");
            }
            if (raw == null || raw.length != KEY_BYTES) {
                throw new IllegalArgumentException("La clé '" + id + "' doit faire exactement 32 octets (AES-256)");
            }
            copy.put(id, new SecretKeySpec(raw.clone(), "AES"));
        });
        if (!copy.containsKey(currentKeyId)) {
            throw new IllegalArgumentException("La clé courante '" + currentKeyId + "' est absente du trousseau");
        }
        return new EncryptionKeyring(currentKeyId, Map.copyOf(copy));
    }

    public String currentKeyId() {
        return currentKeyId;
    }

    SecretKey current() {
        return keys.get(currentKeyId);
    }

    SecretKey find(String keyId) {
        return keys.get(keyId);
    }

    @Override
    public String toString() {
        return "EncryptionKeyring[current=" + currentKeyId + ", keys=" + keys.size() + "]";
    }
}
