package com.nexus;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.api.SystemInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Black-box, full-context verification of the Phase 1 API skeleton against a
 * real ephemeral PostgreSQL (no mocked DB layer — see CLAUDE.md on why
 * Testcontainers-backed tests are preferred for anything touching real
 * infrastructure semantics). Uses {@link RestTestClient} — Spring Framework
 * 7's synchronous servlet test client, which replaces {@code
 * TestRestTemplate} (removed in Spring Boot 4; see
 * docs/adr/0006-spring-boot-4-vs-3.md on verifying rather than assuming
 * 3.x-era APIs carry over).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = NexusApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NexusApiApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void healthEndpointIsPublicAndReportsUp() {
        String body = client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("\"status\":\"UP\"");
    }

    @Test
    void livenessAndReadinessProbesArePublicAndUp() {
        client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
        client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
    }

    @Test
    void systemInfoEndpointIsPublicAndReturnsMetadata() {
        SystemInfoResponse body = client.get().uri("/api/v1/system/info").exchange()
                .expectStatus().isOk()
                .expectBody(SystemInfoResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.application()).isNotBlank();
        assertThat(body.javaVersion()).isNotBlank();
        assertThat(body.activeProfiles()).contains("test");
    }

    @Test
    void unauthenticatedRequestToAnUnmappedProtectedPathIsRejectedWithStandardErrorBody() {
        String body = client.get().uri("/api/v1/some-future-endpoint").exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(body).contains("\"traceId\"");
        assertThat(body).contains("\"timestamp\"");
    }

    @Test
    void sensitiveActuatorEndpointsAreNotPubliclyExposed() {
        org.springframework.http.HttpStatusCode status = client.get().uri("/actuator/env").exchange()
                .expectBody(String.class)
                .returnResult()
                .getStatus();

        assertThat(status.value()).isIn(404, 401, 403);
    }

    @Test
    void correlationIdIsEchoedBackOnTheResponse() {
        client.get().uri("/api/v1/system/info")
                .header("X-Correlation-Id", "test-correlation-id-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "test-correlation-id-123");
    }
}
