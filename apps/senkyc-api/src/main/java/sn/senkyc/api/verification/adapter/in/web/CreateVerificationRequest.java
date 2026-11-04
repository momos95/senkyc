package sn.senkyc.api.verification.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import sn.senkyc.api.verification.domain.DocumentType;
import sn.senkyc.api.verification.domain.IssuingCountry;

record CreateVerificationRequest(
        @NotNull DocumentType documentType,
        @NotNull IssuingCountry issuingCountry,
        @Pattern(regexp = "^[\\x20-\\x7E]{1,128}$", message = "must be 1 to 128 printable ASCII characters")
                String externalReference) {}
