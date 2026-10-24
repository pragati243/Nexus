package com.nexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.NexusApiApplication;
import com.nexus.security.devauth.TestTokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Covers the authentication layer only — whether a request gets past
 * {@code SecurityConfig}'s {@code anyRequest().authenticated()} at all.
 * {@link TenantAuthorizationIT} covers what happens after that.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = NexusApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestTokenIssuer testTokenIssuer;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void requestWithNoAuthorizationHeaderIsRejected() {
        String body = client.get().uri("/api/v1/integrations").exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void requestWithGarbageBearerTokenIsRejected() {
        client.get().uri("/api/v1/integrations")
                .header("Authorization", "Bearer this-is-not-a-jwt")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requestWithExpiredTokenIsRejected() {
        String expiredToken = testTokenIssuer.issueExpired("user-" + java.util.UUID.randomUUID());

        client.get().uri("/api/v1/integrations")
                .header("Authorization", "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validSignedTokenIsAcceptedByAuthenticationEvenWithoutAnyTenantMembership() {
        // Authentication succeeds on a well-formed, unexpired, correctly-signed token alone —
        // authorization (does this principal belong to a tenant) is a separate, later check,
        // covered in TenantAuthorizationIT. This asserts the response is NOT 401; it may still
        // be 403 once tenant resolution runs.
        String token = testTokenIssuer.issue("user-with-no-memberships-" + java.util.UUID.randomUUID());

        var status = client.get().uri("/api/v1/integrations")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody(String.class)
                .returnResult()
                .getStatus();

        assertThat(status.value()).isNotEqualTo(401);
    }

    @Test
    void devTokenMintingEndpointIsPublicAndReachableInTestProfile() {
        String body = client.post().uri("/api/v1/dev/tokens")
                .header("Content-Type", "application/json")
                .bodyValue(new com.nexus.security.devauth.DevTokenRequest("user-123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("\"accessToken\"");
        assertThat(body).contains("\"tokenType\":\"Bearer\"");
    }
}
