package sn.senkyc.api.storage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtectedObjectStoreTest {

    static class InMemoryStorage implements ObjectStorage {
        final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public String location() {
            return "memory";
        }

        @Override
        public void put(String objectKey, byte[] content) {
            objects.put(objectKey, content.clone());
        }

        @Override
        public Optional<byte[]> get(String objectKey) {
            return Optional.ofNullable(objects.get(objectKey)).map(byte[]::clone);
        }

        @Override
        public void delete(String objectKey) {
            objects.remove(objectKey);
        }
    }

    private final InMemoryStorage storage = new InMemoryStorage();
    private final ProtectedObjectStore store = new ProtectedObjectStore(
            storage, new ObjectCipher(EncryptionKeyring.of("k1", Map.of("k1", new byte[EncryptionKeyring.KEY_BYTES]))));
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID verification = UUID.randomUUID();
    private static final byte[] DATA = "contenu fictif".getBytes(StandardCharsets.UTF_8);

    @Test
    void storesEncryptedAndReadsBackThePlaintext() {
        StoredObjectRef ref = store.store(tenantA, verification, DATA);

        assertThat(new String(storage.objects.get(ref.objectKey()), StandardCharsets.ISO_8859_1))
                .doesNotContain("contenu fictif");
        assertThat(store.read(tenantA, ref.objectKey())).hasValue(DATA);
        assertThat(ref.sizeBytes()).isEqualTo(DATA.length);
        assertThat(ref.sha256()).hasSize(64);
        assertThat(ref.encryptionKeyId()).isEqualTo("k1");
    }

    @Test
    void objectKeyContainsOnlyIdentifiers() {
        StoredObjectRef ref = store.store(tenantA, verification, DATA);

        assertThat(ref.objectKey()).startsWith("tenants/" + tenantA + "/verifications/" + verification + "/");
    }

    @Test
    void anotherTenantCannotReadOrDelete() {
        StoredObjectRef ref = store.store(tenantA, verification, DATA);

        assertThatThrownBy(() -> store.read(tenantB, ref.objectKey())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.delete(tenantB, ref.objectKey())).isInstanceOf(IllegalArgumentException.class);
        assertThat(storage.objects).containsKey(ref.objectKey());
    }

    @Test
    void pathTraversalIsRejected() {
        assertThatThrownBy(() -> store.read(tenantA, "tenants/" + tenantA + "/../" + tenantB + "/x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingObjectReadsAsEmptyAndDeleteIsIdempotent() {
        String key = "tenants/" + tenantA + "/verifications/" + verification + "/" + UUID.randomUUID();

        assertThat(store.read(tenantA, key)).isEmpty();
        store.delete(tenantA, key);
    }

    @Test
    void deleteRemovesTheObject() {
        StoredObjectRef ref = store.store(tenantA, verification, DATA);

        store.delete(tenantA, ref.objectKey());

        assertThat(store.read(tenantA, ref.objectKey())).isEmpty();
    }

    @Test
    void corruptedObjectFailsClosed() {
        StoredObjectRef ref = store.store(tenantA, verification, DATA);
        storage.objects.get(ref.objectKey())[storage.objects.get(ref.objectKey()).length - 1] ^= 1;

        assertThatThrownBy(() -> store.read(tenantA, ref.objectKey())).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    void objectCopiedToAnotherTenantsKeyDoesNotDecrypt() {
        StoredObjectRef ref = store.store(tenantA, verification, DATA);
        String stolenKey = "tenants/" + tenantB + "/verifications/" + verification + "/" + UUID.randomUUID();
        storage.objects.put(stolenKey, storage.objects.get(ref.objectKey()));

        assertThatThrownBy(() -> store.read(tenantB, stolenKey)).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    void emptyAndOversizedContentsAreRejected() {
        assertThatThrownBy(() -> store.store(tenantA, verification, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.store(
                        tenantA, verification, new byte[ProtectedObjectStore.MAX_PLAINTEXT_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Arrays.asList(storage.objects.keySet().toArray())).isEmpty();
    }
}
