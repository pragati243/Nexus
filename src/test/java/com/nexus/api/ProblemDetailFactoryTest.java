package com.nexus.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@ExtendWith(MockitoExtension.class)
class ProblemDetailFactoryTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void createPopulatesStandardFieldsAndInstanceFromRequest() {
        when(request.getRequestURI()).thenReturn("/api/v1/widgets");

        ProblemDetail problem = ProblemDetailFactory.create(
                HttpStatus.NOT_FOUND, "Not Found", "no such widget",
                ProblemDetailFactory.codeFor(HttpStatus.NOT_FOUND), request);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getDetail()).isEqualTo("no such widget");
        assertThat(problem.getInstance()).hasToString("/api/v1/widgets");
        assertThat(problem.getProperties()).containsEntry("code", "NOT_FOUND");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void createWithoutRequestLeavesInstanceUnset() {
        ProblemDetail problem = ProblemDetailFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "boom",
                ProblemDetailFactory.codeFor(HttpStatus.INTERNAL_SERVER_ERROR), null);

        assertThat(problem.getInstance()).isNull();
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    void codeForMapsKnownStatusesToStableCodes() {
        assertThat(ProblemDetailFactory.codeFor(HttpStatus.BAD_REQUEST)).isEqualTo("VALIDATION_ERROR");
        assertThat(ProblemDetailFactory.codeFor(HttpStatus.UNAUTHORIZED)).isEqualTo("UNAUTHENTICATED");
        assertThat(ProblemDetailFactory.codeFor(HttpStatus.FORBIDDEN)).isEqualTo("FORBIDDEN");
        assertThat(ProblemDetailFactory.codeFor(HttpStatus.CONFLICT)).isEqualTo("CONFLICT");
        assertThat(ProblemDetailFactory.codeFor(HttpStatus.UNPROCESSABLE_CONTENT)).isEqualTo("UNPROCESSABLE_ENTITY");
        assertThat(ProblemDetailFactory.codeFor(HttpStatus.TOO_MANY_REQUESTS)).isEqualTo("RATE_LIMITED");
        assertThat(ProblemDetailFactory.codeFor(HttpStatus.INTERNAL_SERVER_ERROR)).isEqualTo("INTERNAL_ERROR");
    }
}
