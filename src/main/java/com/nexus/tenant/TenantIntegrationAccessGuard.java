package com.nexus.tenant;

import com.nexus.integration.Integration;
import com.nexus.integration.IntegrationRepository;
import com.nexus.integration.IntegrationStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The background-processing counterpart to {@link TenantContextResolver}.
 * There is no HTTP request, no JWT, no {@code SecurityContextHolder} when a
 * worker picks up a queue message — it only has whatever {@code tenantId}
 * and {@code integrationId} the message payload claims, and
 * architecture.md § 8 (point 4) requires those claims to be re-validated
 * against the database before acting on them, because a message can sit in
 * a queue across a tenant being suspended or an integration being disabled.
 *
 * <p>Reaches into {@code com.nexus.integration}'s repository directly,
 * which is otherwise against this codebase's module-boundary convention
 * (CLAUDE.md) — accepted here because the check is a narrow, read-only leaf
 * ("is tenant X active, and does integration Y both belong to X and remain
 * active"), not business composition across modules. If this guard grows
 * beyond that one check, it should become an explicit service interface
 * instead.
 */
@Service
public class TenantIntegrationAccessGuard {

    private final TenantRepository tenantRepository;
    private final IntegrationRepository integrationRepository;

    public TenantIntegrationAccessGuard(TenantRepository tenantRepository,
            IntegrationRepository integrationRepository) {
        this.tenantRepository = tenantRepository;
        this.integrationRepository = integrationRepository;
    }

    @Transactional(readOnly = true)
    public void ensureActiveForProcessing(UUID tenantId, UUID integrationId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantAccessDeniedException("Tenant " + tenantId + " does not exist."));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new TenantAccessDeniedException("Tenant " + tenantId + " is not active.");
        }

        Integration integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new TenantAccessDeniedException(
                        "Integration " + integrationId + " does not belong to tenant " + tenantId + "."));
        if (integration.getStatus() != IntegrationStatus.ACTIVE) {
            throw new TenantAccessDeniedException("Integration " + integrationId + " is not active.");
        }
    }
}
