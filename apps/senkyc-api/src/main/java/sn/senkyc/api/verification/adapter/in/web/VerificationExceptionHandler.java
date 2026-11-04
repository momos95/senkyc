package sn.senkyc.api.verification.adapter.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sn.senkyc.api.platform.web.ApiExceptionHandler;
import sn.senkyc.api.storage.application.StorageException;
import sn.senkyc.api.verification.application.InvalidImageException;
import sn.senkyc.api.verification.application.StorageNotConfiguredException;
import sn.senkyc.api.verification.domain.DocumentPartNotAllowedException;
import sn.senkyc.api.verification.domain.DuplicateDocumentPartException;
import sn.senkyc.api.verification.domain.IdempotencyConflictException;
import sn.senkyc.api.verification.domain.TenantSuspendedException;
import sn.senkyc.api.verification.domain.UnsupportedCountryException;
import sn.senkyc.api.verification.domain.VerificationNotFoundException;
import sn.senkyc.api.verification.domain.VerificationNotOpenException;

/** Erreurs du module vérification : messages génériques, aucun détail interne ni contenu de l'image. */
@RestControllerAdvice(assignableTypes = VerificationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class VerificationExceptionHandler {

    @ExceptionHandler(VerificationNotFoundException.class)
    ProblemDetail notFound() {
        return ApiExceptionHandler.problem(HttpStatus.NOT_FOUND, "Verification not found", "The verification does not exist.");
    }

    @ExceptionHandler(UnsupportedCountryException.class)
    ProblemDetail unsupportedCountry() {
        return ApiExceptionHandler.problem(
                HttpStatus.UNPROCESSABLE_ENTITY, "Country not enabled", "This issuing country is not enabled.");
    }

    @ExceptionHandler(TenantSuspendedException.class)
    ProblemDetail suspended() {
        return ApiExceptionHandler.problem(HttpStatus.FORBIDDEN, "Tenant suspended", "This tenant is suspended.");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict() {
        return ApiExceptionHandler.problem(
                HttpStatus.CONFLICT,
                "Idempotency key reused",
                "This Idempotency-Key was already used for a different request.");
    }

    @ExceptionHandler(VerificationNotOpenException.class)
    ProblemDetail notOpen() {
        return ApiExceptionHandler.problem(
                HttpStatus.CONFLICT, "Verification closed", "This verification no longer accepts documents.");
    }

    @ExceptionHandler(DuplicateDocumentPartException.class)
    ProblemDetail duplicatePart() {
        return ApiExceptionHandler.problem(
                HttpStatus.CONFLICT, "Document already submitted", "This part was already submitted.");
    }

    @ExceptionHandler(DocumentPartNotAllowedException.class)
    ProblemDetail partNotAllowed() {
        return ApiExceptionHandler.problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Part not allowed",
                "This part does not exist for this document type.");
    }

    @ExceptionHandler(InvalidImageException.class)
    ProblemDetail invalidImage(InvalidImageException ex) {
        ProblemDetail problem = ApiExceptionHandler.problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Invalid image",
                "Accepted: a JPEG or PNG image, 20 KiB to 8 MiB, at least 480 px on its shorter side.");
        problem.setProperty("reason", ex.reason().name());
        return problem;
    }

    @ExceptionHandler({StorageNotConfiguredException.class, StorageException.class})
    ProblemDetail storageUnavailable() {
        return ApiExceptionHandler.problem(
                HttpStatus.SERVICE_UNAVAILABLE, "Storage unavailable", "Document storage is currently unavailable.");
    }
}
