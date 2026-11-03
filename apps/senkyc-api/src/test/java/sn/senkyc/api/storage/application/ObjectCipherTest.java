package sn.senkyc.api.storage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectCipherTest {

    private static byte[] key(int fill) {
        byte[] k = new byte[EncryptionKeyring.KEY_BYTES];
        Arrays.fill(k, (byte) fill);
        return k;
    }

    private static ObjectCipher cipher() {
        return new ObjectCipher(EncryptionKeyring.of("k1", Map.of("k1", key(1))));
    }

    private static final byte[] PLAINTEXT = "image fictive".getBytes(StandardCharsets.UTF_8);

    @Test
    void roundTripRestoresThePlaintext() {
        ObjectCipher cipher = cipher();

        byte[] blob = cipher.encrypt(PLAINTEXT, "tenants/a/obj");

        assertThat(cipher.decrypt(blob, "tenants/a/obj")).isEqualTo(PLAINTEXT);
    }

    @Test
    void ciphertextDoesNotContainThePlaintext() {
        byte[] blob = cipher().encrypt(PLAINTEXT, "k");

        assertThat(new String(blob, StandardCharsets.ISO_8859_1)).doesNotContain("image fictive");
    }

    @Test
    void twoEncryptionsOfTheSameContentDiffer() {
        ObjectCipher cipher = cipher();

        assertThat(cipher.encrypt(PLAINTEXT, "k")).isNotEqualTo(cipher.encrypt(PLAINTEXT, "k"));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        ObjectCipher cipher = cipher();
        byte[] blob = cipher.encrypt(PLAINTEXT, "k");
        blob[blob.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(blob, "k")).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    void objectMovedUnderAnotherKeyIsRejected() {
        ObjectCipher cipher = cipher();
        byte[] blob = cipher.encrypt(PLAINTEXT, "tenants/a/obj");

        assertThatThrownBy(() -> cipher.decrypt(blob, "tenants/b/obj")).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    void malformedOrUnknownBlobsAreRejected() {
        ObjectCipher cipher = cipher();

        assertThatThrownBy(() -> cipher.decrypt(new byte[0], "k")).isInstanceOf(DecryptionFailedException.class);
        assertThatThrownBy(() -> cipher.decrypt(new byte[40], "k")).isInstanceOf(DecryptionFailedException.class);
        byte[] unknownVersion = cipher.encrypt(PLAINTEXT, "k");
        unknownVersion[0] = 9;
        assertThatThrownBy(() -> cipher.decrypt(unknownVersion, "k")).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    void anotherKeyCannotDecrypt() {
        byte[] blob = cipher().encrypt(PLAINTEXT, "k");
        ObjectCipher other = new ObjectCipher(EncryptionKeyring.of("k1", Map.of("k1", key(2))));

        assertThatThrownBy(() -> other.decrypt(blob, "k")).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    void rotationKeepsOldObjectsReadable() {
        byte[] oldBlob = cipher().encrypt(PLAINTEXT, "k");
        ObjectCipher rotated =
                new ObjectCipher(EncryptionKeyring.of("k2", Map.of("k1", key(1), "k2", key(2))));

        assertThat(rotated.decrypt(oldBlob, "k")).isEqualTo(PLAINTEXT);
        assertThat(rotated.currentKeyId()).isEqualTo("k2");
        assertThat(rotated.decrypt(rotated.encrypt(PLAINTEXT, "k"), "k")).isEqualTo(PLAINTEXT);
    }

    @Test
    void keyringRejectsInvalidKeys() {
        assertThatThrownBy(() -> EncryptionKeyring.of("k1", Map.of("k1", new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncryptionKeyring.of("k2", Map.of("k1", key(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncryptionKeyring.of("K1", Map.of("K1", key(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keyringToStringNeverShowsKeys() {
        assertThat(EncryptionKeyring.of("k1", Map.of("k1", key(7))).toString()).doesNotContain("7", "AES");
    }
}
