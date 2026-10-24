package com.nexus.tenant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.NexusWorkerApplication;
import com.nexus.integration.Integration;
import com.nexus.integration.IntegrationConnectionType;
import com.nexus.integration.IntegrationRepository;
import com.nexus.integration.IntegrationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The background-processing half of tenant access control: no HTTP request,
 * no JWT, just a {@code tenantId}/{@code integrationId} pair the way a
 * worker would receive them in an SQS message payload. Uses
 * {@code NexusWorkerApplication} as the context — no web stack, matching
 * what the real worker actually runs as (architecture.md § 8, point 4).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = NexusWorkerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TenantIntegrationAccessGuardIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private IntegrationRepository integrationRepository;
    @Autowired
    private TenantIntegrationAccessGuard guard;

    private Tenant newTenant(TenantStatus status) {
        return tenantRepository.saveAndFlush(
                new Tenant(UUID.randomUUID(), "Acme", "acme-" + UUID.randomUUID(), status));
    }

    private Integration newIntegration(UUID tenantId, IntegrationStatus status) {
        return integrationRepository.saveAndFlush(new Integration(
                UUID.randomUUID(), tenantId, "Acme CRM", "CRM", IntegrationConnectionType.FILE_UPLOAD,
                "{}", status));
    }

    @Test
    void activeTenantAndActiveIntegrationPassesValidation() {
        Tenant tenant = newTenant(TenantStatus.ACTIVE);
        Integration integration = newIntegration(tenant.getId(), IntegrationStatus.ACTIVE);

        assertThatCode(() -> guard.ensureActiveForProcessing(tenant.getId(), integration.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void suspendedTenantIsRejectedEvenIfTheIntegrationIsActive() {
        Tenant tenant = newTenant(TenantStatus.SUSPENDED);
        Integration integration = newIntegration(tenant.getId(), IntegrationStatus.ACTIVE);

        assertThatThrownBy(() -> guard.ensureActiveForProcessing(tenant.getId(), integration.getId()))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void disabledIntegrationIsRejectedEvenIfTheTenantIsActive() {
        Tenant tenant = newTenant(TenantStatus.ACTIVE);
        Integration integration = newIntegration(tenant.getId(), IntegrationStatus.DISABLED);

        assertThatThrownBy(() -> guard.ensureActiveForProcessing(tenant.getId(), integration.getId()))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void integrationBelongingToADifferentTenantIsRejected() {
        Tenant claimedTenant = newTenant(TenantStatus.ACTIVE);
        Tenant actualOwner = newTenant(TenantStatus.ACTIVE);
        Integration integration = newIntegration(actualOwner.getId(), IntegrationStatus.ACTIVE);

        assertThatThrownBy(() -> guard.ensureActiveForProcessing(claimedTenant.getId(), integration.getId()))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void nonExistentTenantIsRejected() {
        assertThatThrownBy(() -> guard.ensureActiveForProcessing(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(TenantAccessDeniedException.class);
    }
}
