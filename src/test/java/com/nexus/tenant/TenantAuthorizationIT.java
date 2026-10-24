package com.nexus.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.NexusApiApplication;
import com.nexus.integration.CreateIntegrationRequest;
import com.nexus.integration.Integration;
import com.nexus.integration.IntegrationConnectionType;
import com.nexus.integration.IntegrationRepository;
import com.nexus.integration.IntegrationStatus;
import com.nexus.security.devauth.TestTokenIssuer;
import java.util.Map;
import java.util.UUID;
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
 * Role restrictions, cross-tenant access via a real (not guessed) foreign
 * id, and {@code X-Tenant-Id} selection/ambiguity — the scenarios
 * docs/threat-model.md § 2 exists to prevent, exercised through the real
 * HTTP + authorization stack rather than at the repository layer alone
 * (which Phase 2's {@code ConstraintsIT} already covers for the database
 * side of tenant isolation).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = NexusApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantAuthorizationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private TenantMembershipRepository tenantMembershipRepository;
    @Autowired
    private IntegrationRepository integrationRepository;
    @Autowired
    private TestTokenIssuer testTokenIssuer;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private Tenant newTenant() {
        return tenantRepository.saveAndFlush(
                new Tenant(UUID.randomUUID(), "Acme", "acme-" + UUID.randomUUID(), TenantStatus.ACTIVE));
    }

    private void membership(UUID tenantId, String principalId, MembershipRole role) {
        tenantMembershipRepository.saveAndFlush(new TenantMembership(UUID.randomUUID(), tenantId, principalId, role));
    }

    private Integration newIntegration(UUID tenantId) {
        return integrationRepository.saveAndFlush(new Integration(
                UUID.randomUUID(), tenantId, "Acme CRM", "CRM", IntegrationConnectionType.FILE_UPLOAD,
                "{}", IntegrationStatus.ACTIVE));
    }

    private CreateIntegrationRequest createIntegrationRequestBody() {
        return new CreateIntegrationRequest("Acme CRM", "CRM", IntegrationConnectionType.FILE_UPLOAD, Map.of());
    }

    @Test
    void readOnlyRoleCannotCreateAnIntegration() {
        Tenant tenant = newTenant();
        String principal = "user-" + UUID.randomUUID();
        membership(tenant.getId(), principal, MembershipRole.READ_ONLY);
        String token = testTokenIssuer.issue(principal);

        client.post().uri("/api/v1/integrations")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .bodyValue(createIntegrationRequestBody())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void operatorRoleCannotCreateAnIntegrationEither() {
        Tenant tenant = newTenant();
        String principal = "user-" + UUID.randomUUID();
        membership(tenant.getId(), principal, MembershipRole.OPERATOR);
        String token = testTokenIssuer.issue(principal);

        client.post().uri("/api/v1/integrations")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .bodyValue(createIntegrationRequestBody())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantAdminCanCreateAnIntegration() {
        Tenant tenant = newTenant();
        String principal = "user-" + UUID.randomUUID();
        membership(tenant.getId(), principal, MembershipRole.TENANT_ADMIN);
        String token = testTokenIssuer.issue(principal);

        String body = client.post().uri("/api/v1/integrations")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .bodyValue(createIntegrationRequestBody())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("\"tenantId\":\"" + tenant.getId() + "\"");
    }

    @Test
    void readOnlyRoleCanStillListAndReadIntegrations() {
        Tenant tenant = newTenant();
        String principal = "user-" + UUID.randomUUID();
        membership(tenant.getId(), principal, MembershipRole.READ_ONLY);
        Integration integration = newIntegration(tenant.getId());
        String token = testTokenIssuer.issue(principal);

        client.get().uri("/api/v1/integrations/" + integration.getId())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void tenantACannotReadTenantBsIntegrationByItsRealId() {
        Tenant tenantA = newTenant();
        Tenant tenantB = newTenant();
        String principalA = "user-" + UUID.randomUUID();
        membership(tenantA.getId(), principalA, MembershipRole.TENANT_ADMIN);
        Integration tenantBsIntegration = newIntegration(tenantB.getId());
        String tokenA = testTokenIssuer.issue(principalA);

        String body = client.get().uri("/api/v1/integrations/" + tenantBsIntegration.getId())
                .header("Authorization", "Bearer " + tokenA)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // Confirms this is the same "not found" shape as a genuinely nonexistent id — not a
        // 403, which would leak that the id belongs to someone (docs/threat-model.md § 2).
        assertThat(body).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void guessingAnotherTenantsRealIntegrationIdStillFails() {
        // Same scenario as above, phrased as the "direct-ID access" case explicitly: even
        // knowing a REAL, valid UUID belonging to another tenant grants nothing.
        Tenant tenantA = newTenant();
        Tenant tenantB = newTenant();
        String principalA = "user-" + UUID.randomUUID();
        membership(tenantA.getId(), principalA, MembershipRole.READ_ONLY);
        Integration tenantBsIntegration = newIntegration(tenantB.getId());
        String tokenA = testTokenIssuer.issue(principalA);

        client.get().uri("/api/v1/integrations/" + tenantBsIntegration.getId())
                .header("Authorization", "Bearer " + tokenA)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void principalWithNoMembershipsIsForbiddenEverywhere() {
        String principal = "user-" + UUID.randomUUID();
        String token = testTokenIssuer.issue(principal);

        client.get().uri("/api/v1/integrations")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void principalWithTwoMembershipsAndNoTenantHeaderIsAmbiguous() {
        Tenant tenantA = newTenant();
        Tenant tenantB = newTenant();
        String principal = "user-" + UUID.randomUUID();
        membership(tenantA.getId(), principal, MembershipRole.READ_ONLY);
        membership(tenantB.getId(), principal, MembershipRole.READ_ONLY);
        String token = testTokenIssuer.issue(principal);

        client.get().uri("/api/v1/integrations")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tenantHeaderCorrectlySelectsAmongMultipleMemberships() {
        Tenant tenantA = newTenant();
        Tenant tenantB = newTenant();
        String principal = "user-" + UUID.randomUUID();
        membership(tenantA.getId(), principal, MembershipRole.READ_ONLY);
        membership(tenantB.getId(), principal, MembershipRole.READ_ONLY);
        newIntegration(tenantB.getId());
        String token = testTokenIssuer.issue(principal);

        String body = client.get().uri("/api/v1/integrations")
                .header("Authorization", "Bearer " + token)
                .header(TenantContextArgumentResolver.TENANT_HEADER, tenantB.getId().toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("\"totalElements\":1");
    }

    @Test
    void tenantHeaderNamingATenantTheCallerDoesNotBelongToIsForbidden() {
        Tenant tenantA = newTenant();
        Tenant unrelatedTenant = newTenant();
        String principal = "user-" + UUID.randomUUID();
        membership(tenantA.getId(), principal, MembershipRole.TENANT_ADMIN);
        String token = testTokenIssuer.issue(principal);

        client.get().uri("/api/v1/integrations")
                .header("Authorization", "Bearer " + token)
                .header(TenantContextArgumentResolver.TENANT_HEADER, unrelatedTenant.getId().toString())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }
}
