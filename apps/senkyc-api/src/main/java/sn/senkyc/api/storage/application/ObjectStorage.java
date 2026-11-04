package sn.senkyc.api.storage.application;

import java.util.Optional;

/**
 * Port de stockage d'objets. Il ne manipule que des octets DÉJÀ chiffrés : le chiffrement est la
 * responsabilité de {@link ProtectedObjectStore}, pas du fournisseur (RustFS, Garage, S3 managé...).
 */
public interface ObjectStorage {

    /** Nom du conteneur (bucket) où sont écrits les objets : conservé avec les métadonnées. */
    String location();

    void put(String objectKey, byte[] content);

    Optional<byte[]> get(String objectKey);

    /** Idempotent : supprimer un objet absent n'est pas une erreur. */
    void delete(String objectKey);
}
