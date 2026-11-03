package sn.senkyc.api.tenant.adapter.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sn.senkyc.api.platform.web.ApiExceptionHandler;
import sn.senkyc.api.tenant.domain.DuplicateTenantCodeException;
import sn.senkyc.api.tenant.domain.TenantNotFoundException;

/** Erreurs propres au module tenant ; prioritaire sur le gestionnaire générique de la plateforme. */
@RestControllerAdvice(assignableTypes = TenantController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class TenantExceptionHandler {

    @ExceptionHandler(TenantNotFoundException.class)
    ProblemDetail handleNotFound(TenantNotFoundException ex) {
        return ApiExceptionHandler.problem(HttpStatus.NOT_FOUND, "Tenant not found", "The tenant does not exist.");
    }

    @ExceptionHandler(DuplicateTenantCodeException.class)
    ProblemDetail handleDuplicate(DuplicateTenantCodeException ex) {
        return ApiExceptionHandler.problem(
                HttpStatus.CONFLICT, "Tenant code already used", "A tenant with this code already exists.");
    }
}
