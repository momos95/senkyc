package sn.senkyc.api.verification.domain;

/** Image soumise pour une vérification (alignée sur la contrainte {@code ck_stored_object_kind}). */
public enum DocumentPart {
    DOCUMENT_FRONT,
    DOCUMENT_BACK,
    SELFIE;

    /** Le passeport n'a pas de verso : seule sa page d'identité est soumise. */
    public boolean isAllowedFor(DocumentType type) {
        return !(this == DOCUMENT_BACK && type == DocumentType.PASSPORT);
    }
}
