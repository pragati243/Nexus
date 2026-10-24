package com.nexus.ingestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRecordRepository extends JpaRepository<SourceRecord, UUID> {

    /** The reconciliation matching query (Phase 4) — backed by the (tenant_id, integration_id, business_key) index. */
    List<SourceRecord> findByTenantIdAndIntegrationIdAndBusinessKey(
            UUID tenantId, UUID integrationId, String businessKey);

    List<SourceRecord> findByIngestionJobId(UUID ingestionJobId);

    Optional<SourceRecord> findByTenantIdAndIntegrationIdAndSourceSystemAndExternalRecordId(
            UUID tenantId, UUID integrationId, String sourceSystem, String externalRecordId);
}
