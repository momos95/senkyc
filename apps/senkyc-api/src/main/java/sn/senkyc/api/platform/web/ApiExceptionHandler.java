package sn.senkyc.api.platform.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import sn.senkyc.api.platform.tenant.TenantContextMissingException;

/**
 * Gestion centralisée des erreurs au format RFC 9457 (ProblemDetail). Aucun détail interne
 * (message d'exception, pile) n'est renvoyé au client ; l'identifiant de corrélation permet le suivi.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(TenantContextMissingException.class)
    ProblemDetail handleTenantMissing(TenantContextMissingException ex) {
        return problem(HttpStatus.FORBIDDEN, "Tenant required", "This operation requires a tenant-scoped token.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex; // gérées par la chaîne de sécurité (401/403)
        }
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problemDetail) {
            problemDetail.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    public static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }
}
