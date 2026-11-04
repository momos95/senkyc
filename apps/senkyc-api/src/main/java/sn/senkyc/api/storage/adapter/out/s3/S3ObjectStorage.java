package sn.senkyc.api.storage.adapter.out.s3;

import java.util.Optional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import sn.senkyc.api.storage.application.ObjectStorage;
import sn.senkyc.api.storage.application.StorageException;

/** Adaptateur du port {@link ObjectStorage} pour tout serveur compatible S3 (RustFS, Garage, S3...). */
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final String bucket;

    public S3ObjectStorage(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    /**
     * Vérifie que le bucket existe, le crée sinon. Aucune politique publique ni ACL n'est posée : un
     * bucket S3 est privé par défaut.
     */
    public void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            create();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                create();
            } else {
                throw new StorageException("Bucket inaccessible (HTTP " + e.statusCode() + ")", e);
            }
        } catch (SdkException e) {
            throw new StorageException("Serveur de stockage injoignable", e);
        }
    }

    private void create() {
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (SdkException e) {
            throw new StorageException("Création du bucket impossible", e);
        }
    }

    @Override
    public String location() {
        return bucket;
    }

    @Override
    public void put(String objectKey, byte[] content) {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType("application/octet-stream")
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new StorageException("Écriture impossible", e);
        }
    }

    @Override
    public Optional<byte[]> get(String objectKey) {
        try {
            return Optional.of(client.getObjectAsBytes(
                            GetObjectRequest.builder().bucket(bucket).key(objectKey).build())
                    .asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkException e) {
            throw new StorageException("Lecture impossible", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (SdkException e) {
            throw new StorageException("Suppression impossible", e);
        }
    }
}
