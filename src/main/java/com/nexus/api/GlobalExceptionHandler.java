package com.nexus.api;

import com.nexus.shared.ResourceNotFoundException;
import com.nexus.tenant.AmbiguousTenantSelectionException;
import com.nexus.tenant.TenantAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every exception that escapes a controller into the standard
 * error body (see {@link ProblemDetailFactory}). Extends
 * {@link ResponseEntityExceptionHandler} to reuse its built-in handling of
 * Spring MVC exceptions (validation, malformed JSON, unsupported media
 * type, ...) and enriches every one of those responses uniformly via the
 * {@link #createProblemDetail} override, instead of re-implementing each
 * case by hand.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetailFactory.create(
                HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(),
                ProblemDetailFactory.codeFor(HttpStatus.NOT_FOUND), servletRequestOf(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ResponseEntity<Object> handleTenantAccessDenied(TenantAccessDeniedException ex, WebRequest request) {
        log.warn("Tenant authorization denied: {} [path={}]", ex.getMessage(),
                servletRequestOf(request) != null ? servletRequestOf(request).getRequestURI() : "unknown");
        ProblemDetail problemDetail = ProblemDetailFactory.create(
                HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(),
                ProblemDetailFactory.codeFor(HttpStatus.FORBIDDEN), servletRequestOf(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
    }

    @ExceptionHandler(AmbiguousTenantSelectionException.class)
    public ResponseEntity<Object> handleAmbiguousTenant(AmbiguousTenantSelectionException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetailFactory.create(
                HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(),
                "AMBIGUOUS_TENANT_SELECTION", servletRequestOf(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception processing request", ex);
        ProblemDetail problemDetail = ProblemDetailFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred.",
                ProblemDetailFactory.codeFor(HttpStatus.INTERNAL_SERVER_ERROR),
                servletRequestOf(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, org.springframework.http.HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problemDetail = (body instanceof ProblemDetail pd) ? pd
                : ProblemDetailFactory.create(statusCode, statusCode.toString(), ex.getMessage(),
                        ProblemDetailFactory.codeFor(statusCode), servletRequestOf(request));
        enrich(problemDetail, statusCode, request);
        return super.handleExceptionInternal(ex, problemDetail, headers, statusCode, request);
    }

    private void enrich(ProblemDetail problemDetail, HttpStatusCode statusCode, WebRequest request) {
        if (problemDetail.getProperties() == null || !problemDetail.getProperties().containsKey("code")) {
            problemDetail.setProperty("code", ProblemDetailFactory.codeFor(statusCode));
        }
        if (problemDetail.getProperties() == null || !problemDetail.getProperties().containsKey("timestamp")) {
            problemDetail.setProperty("timestamp", java.time.Instant.now());
        }
        String traceId = org.slf4j.MDC.get(com.nexus.observability.CorrelationIdFilter.MDC_KEY);
        if (traceId != null && (problemDetail.getProperties() == null || !problemDetail.getProperties().containsKey("traceId"))) {
            problemDetail.setProperty("traceId", traceId);
        }
        HttpServletRequest servletRequest = servletRequestOf(request);
        if (servletRequest != null && problemDetail.getInstance() == null) {
            problemDetail.setInstance(java.net.URI.create(servletRequest.getRequestURI()));
        }
    }

    private HttpServletRequest servletRequestOf(WebRequest request) {
        return request instanceof ServletWebRequest servletWebRequest ? servletWebRequest.getRequest() : null;
    }
}
