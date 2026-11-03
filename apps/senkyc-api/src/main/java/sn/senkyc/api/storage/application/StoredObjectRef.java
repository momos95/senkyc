package sn.senkyc.api.storage.application;

/**
 * Référence à un objet stocké, à enregistrer dans {@code stored_object}. {@code sha256} et
 * {@code sizeBytes} décrivent le contenu EN CLAIR ; {@code objectKey} ne contient aucune donnée personnelle.
 */
public record StoredObjectRef(String objectKey, String sha256, long sizeBytes, String encryptionKeyId) {}
