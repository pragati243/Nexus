package com.nexus.integration;

import com.nexus.audit.AuditEventRecorder;
import com.nexus.shared.ResourceNotFoundException;
import com.nexus.tenant.MembershipRole;
import com.nexus.tenant.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way a controller touches {@link IntegrationRepository} — every
 * method takes a {@link TenantContext}, and nothing here ever calls the
 * repository's bare {@code findById}. This is the vertical slice Phase 3
 * uses to prove the authorization model end to end (docs/authorization-model.md).
 */
@Service
public class IntegrationService {

    private final IntegrationRepository integrationRepository;
    private final AuditEventRecorder auditEventRecorder;

    public IntegrationService(IntegrationRepository integrationRepository, AuditEventRecorder auditEventRecorder) {
        this.integrationRepository = integrationRepository;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public Integration create(TenantContext context, String name, String sourceSystem,
            IntegrationConnectionType connectionType, String configJson) {
        context.require(MembershipRole.TENANT_ADMIN);

        Integration integration = new Integration(UUID.randomUUID(), context.tenantId(), name, sourceSystem,
                connectionType, configJson, IntegrationStatus.ACTIVE);
        Integration saved = integrationRepository.save(integration);

        auditEventRecorder.record(context.tenantId(), context.principalId(), "INTEGRATION_CREATED",
                "INTEGRATION", saved.getId(), "{}");

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Integration> list(TenantContext context, IntegrationStatus statusFilter, Pageable pageable) {
        // READ_ONLY and above — no role check; every role can list.
        if (statusFilter != null) {
            return integrationRepository.findByTenantIdAndStatus(context.tenantId(), statusFilter, pageable);
        }
        return integrationRepository.findByTenantId(context.tenantId(), pageable);
    }

    @Transactional(readOnly = true)
    public Integration getById(TenantContext context, UUID id) {
        // READ_ONLY and above — no role check; every role can read.
        return integrationRepository.findByIdAndTenantId(id, context.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("No integration " + id + " for this tenant."));
    }
}
