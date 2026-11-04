package sn.senkyc.api.verification.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import sn.senkyc.api.verification.application.VerificationService.VerificationView;
import sn.senkyc.api.verification.domain.StoredDocument;
import sn.senkyc.api.verification.domain.Verification;

/** Vue publique : jamais d'image, de clé d'objet ni de donnée d'identité. */
record VerificationResponse(
        UUID id,
        String status,
        String documentType,
        String issuingCountry,
        String externalReference,
        Instant expiresAt,
        Instant createdAt,
        List<DocumentResponse> documents) {

    record DocumentResponse(String part, String contentType, long sizeBytes, Instant uploadedAt) {

        static DocumentResponse from(StoredDocument d) {
            return new DocumentResponse(d.part().name(), d.contentType(), d.sizeBytes(), d.createdAt());
        }
    }

    static VerificationResponse from(Verification v, List<StoredDocument> documents) {
        return new VerificationResponse(
                v.id(),
                v.status().name(),
                v.documentType().name(),
                v.issuingCountry().name(),
                v.externalReference(),
                v.expiresAt(),
                v.createdAt(),
                documents.stream().map(DocumentResponse::from).toList());
    }

    static VerificationResponse from(VerificationView view) {
        return from(view.verification(), view.documents());
    }
}
