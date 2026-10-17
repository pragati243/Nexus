package com.nexus.api;

import com.nexus.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds the API's standard error body (RFC 9457 {@link ProblemDetail} plus
 * {@code code}, {@code traceId}, {@code timestamp} extensions — see
 * docs/api/initial-contract.md). Used by both MVC exception handling and the
 * Spring Security filter-chain entry point / access-denied handler, which
 * run on different paths but must produce identical response shapes.
 */
public final class ProblemDetailFactory {

    private ProblemDetailFactory() {
    }

    public static ProblemDetail create(HttpStatusCode status, String title, String detail, String code,
            HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("code", code);
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (traceId != null) {
            problemDetail.setProperty("traceId", traceId);
        }
        if (request != null) {
            problemDetail.setInstance(URI.create(request.getRequestURI()));
        }
        return problemDetail;
    }

    public static String codeFor(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved == null) {
            return "ERROR";
        }
        return switch (resolved) {
            case BAD_REQUEST -> "VALIDATION_ERROR";
            case UNAUTHORIZED -> "UNAUTHENTICATED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case UNPROCESSABLE_CONTENT -> "UNPROCESSABLE_ENTITY";
            case TOO_MANY_REQUESTS -> "RATE_LIMITED";
            case INTERNAL_SERVER_ERROR -> "INTERNAL_ERROR";
            default -> "ERROR";
        };
    }
}
