package sn.senkyc.api.verification.application;

import sn.senkyc.api.verification.domain.DocumentType;
import sn.senkyc.api.verification.domain.IssuingCountry;

/** @param idempotencyKey optionnelle ; @param externalReference référence de l'appelant, optionnelle */
public record CreateVerificationCommand(
        DocumentType documentType, IssuingCountry issuingCountry, String externalReference, String idempotencyKey) {}
