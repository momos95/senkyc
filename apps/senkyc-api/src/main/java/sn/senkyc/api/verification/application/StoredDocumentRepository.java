package sn.senkyc.api.verification.application;

import java.util.List;
import java.util.UUID;
import sn.senkyc.api.verification.domain.StoredDocument;

public interface StoredDocumentRepository {

    List<StoredDocument> findByVerification(UUID verificationId);

    /** @return faux si cette partie existait déjà pour la vérification (aucune écriture) */
    boolean insert(StoredDocument document);
}
