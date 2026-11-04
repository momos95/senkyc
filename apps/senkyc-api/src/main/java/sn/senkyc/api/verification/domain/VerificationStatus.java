package sn.senkyc.api.verification.domain;

/** Statuts du cahier des charges (alignés sur la contrainte {@code ck_verification_status}). */
public enum VerificationStatus {
    PENDING,
    PROCESSING,
    APPROVED_TECHNICAL,
    REJECTED,
    MANUAL_REVIEW,
    VERIFIED_OFFICIAL,
    FAILED,
    EXPIRED,
    CANCELLED
}
