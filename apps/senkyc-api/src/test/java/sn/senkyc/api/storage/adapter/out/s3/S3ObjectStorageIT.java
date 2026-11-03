package sn.senkyc.api.storage.adapter.out.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import sn.senkyc.api.storage.application.DecryptionFailedException;
import sn.senkyc.api.storage.application.EncryptionKeyring;
import sn.senkyc.api.storage.application.ObjectCipher;
import sn.senkyc.api.storage.application.ProtectedObjectStore;
import sn.senkyc.api.storage.application.StorageException;
import sn.senkyc.api.storage.application.StoredObjectRef;

/**
 * Test d'intégration contre un vrai serveur RustFS (Testcontainers ; Docker requis, échec sinon).
 * Il vérifie ce dont SenKYC dépend : écriture/lecture/suppression, refus de mauvais identifiants (dont
 * les identifiants par défaut), objet non lisible anonymement, et contenu stocké chiffré.
 * Les identifiants de test sont fictifs et propres au conteneur éphémère.
 */
@Testcontainers
class S3ObjectStorageIT {

    private static final String ACCESS_KEY = "it-access-key";
    private static final String SECRET_KEY = "it-secret-key-not-a-real-secret";
    private static final String BUCKET = "senkyc-it";

    @Container
    static GenericContainer<?> rustfs = new GenericContainer<>(
                    "rustfs/rustfs@sha256:ed9210d6411d77ffceb70d94b964a3cb7d225983368bc25bebe01e995293dcc2")
            .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(90)));

    static S3ObjectStorage storage;
    static ProtectedObjectStore store;

    static String endpoint() {
        return "http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000);
    }

    static S3Client client(String accessKey, String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }

    @BeforeAll
    static void setUp() {
        storage = new S3ObjectStorage(client(ACCESS_KEY, SECRET_KEY), BUCKET);
        // Le port peut écouter avant que le serveur soit prêt : on réessaie (test uniquement).
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1)).ignoreExceptions()
                .untilAsserted(storage::ensureBucket);
        store = new ProtectedObjectStore(
                storage,
                new ObjectCipher(EncryptionKeyring.of("k1", Map.of("k1", new byte[EncryptionKeyring.KEY_BYTES]))));
    }

    @Test
    void ensureBucketIsIdempotent() {
        storage.ensureBucket();
        storage.ensureBucket();
    }

    @Test
    void putGetDeleteRoundTrip() {
        String key = "tenants/t/verifications/v/" + UUID.randomUUID();
        byte[] content = "octets".getBytes(StandardCharsets.UTF_8);

        storage.put(key, content);
        assertThat(storage.get(key)).hasValue(content);
        storage.delete(key);

        assertThat(storage.get(key)).isEmpty();
        storage.delete(key);
    }

    @Test
    void protectedStoreRoundTripAndStoredBytesAreEncrypted() {
        UUID tenant = UUID.randomUUID();
        byte[] image = "marqueur-image-fictive".getBytes(StandardCharsets.UTF_8);

        StoredObjectRef ref = store.store(tenant, UUID.randomUUID(), image);

        assertThat(store.read(tenant, ref.objectKey())).hasValue(image);
        byte[] raw = storage.get(ref.objectKey()).orElseThrow();
        assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain("marqueur-image-fictive");
        store.delete(tenant, ref.objectKey());
        assertThat(store.read(tenant, ref.objectKey())).isEmpty();
    }

    @Test
    void objectTamperedAtRestFailsClosed() {
        UUID tenant = UUID.randomUUID();
        StoredObjectRef ref = store.store(tenant, UUID.randomUUID(), "x".getBytes(StandardCharsets.UTF_8));
        byte[] raw = storage.get(ref.objectKey()).orElseThrow();
        raw[raw.length - 1] ^= 1;
        storage.put(ref.objectKey(), raw);

        assertThatThrownBy(() -> store.read(tenant, ref.objectKey())).isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    void wrongAndDefaultCredentialsAreRejected() {
        assertThatThrownBy(() -> new S3ObjectStorage(client(ACCESS_KEY, "wrong-secret"), BUCKET).get("k"))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> new S3ObjectStorage(client("rustfsadmin", "rustfsadmin"), BUCKET).get("k"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void objectsAreNotReadableAnonymously() throws Exception {
        String key = "tenants/t/verifications/v/" + UUID.randomUUID();
        storage.put(key, "secret".getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(endpoint() + "/" + BUCKET + "/" + key))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body()).doesNotContain("secret");
    }
}
