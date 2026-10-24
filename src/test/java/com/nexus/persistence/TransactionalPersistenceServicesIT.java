package com.nexus.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.NexusWorkerApplication;
import com.nexus.audit.AuditEventRecorder;
import com.nexus.audit.AuditEventRepository;
import com.nexus.integration.Integration;
import com.nexus.integration.IntegrationConnectionType;
import com.nexus.integration.IntegrationRepository;
import com.nexus.integration.IntegrationStatus;
import com.nexus.messaging.OutboxEventWriter;
import com.nexus.shared.IdempotencyKeyService;
import com.nexus.tenant.Tenant;
import com.nexus.tenant.TenantRepository;
import com.nexus.tenant.TenantStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * {@link AuditEventRecorder} and {@link OutboxEventWriter} both require an
 * existing transaction ({@code Propagation.MANDATORY}) so that "written in
 * the same transaction as the business change" (ADR-0003, data-model.md
 * Invariant #7) is something the database enforces, not just something the
 * docs assert. The rollback test below is the real proof: it checks a
 * SEPARATE, later transaction sees neither row — not just that an exception
 * was thrown.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = NexusWorkerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TransactionalPersistenceServicesIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private IntegrationRepository integrationRepository;
    @Autowired
    private AuditEventRecorder auditEventRecorder;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private OutboxEventWriter outboxEventWriter;
    @Autowired
    private IdempotencyKeyService idempotencyKeyService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void auditEventRecorderRefusesToRunOutsideAnExistingTransaction() {
        assertThatThrownBy(() -> auditEventRecorder.record(
                UUID.randomUUID(), "actor", "TEST_ACTION", "TENANT", UUID.randomUUID(), "{}"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void outboxEventWriterRefusesToRunOutsideAnExistingTransaction() {
        assertThatThrownBy(() -> outboxEventWriter.write(
                UUID.randomUUID(), "INGESTION_JOB", UUID.randomUUID(), "IngestionJobQueued", "{}"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void businessWriteAndAuditWriteRollBackTogetherWhenTheSurroundingTransactionFails() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID tenantId = tx.execute(status -> tenantRepository.save(
                new Tenant(UUID.randomUUID(), "Acme", "acme-" + UUID.randomUUID(), TenantStatus.ACTIVE)).getId());
        UUID integrationId = UUID.randomUUID();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            Integration integration = new Integration(
                    integrationId, tenantId, "Acme CRM", "CRM", IntegrationConnectionType.FILE_UPLOAD,
                    "{}", IntegrationStatus.ACTIVE);
            integrationRepository.saveAndFlush(integration);
            auditEventRecorder.record(tenantId, "actor", "INTEGRATION_CREATED", "INTEGRATION", integrationId, "{}");
            throw new RuntimeException("forced failure to exercise rollback");
        })).isInstanceOf(RuntimeException.class).hasMessage("forced failure to exercise rollback");

        // Checked in a brand-new transaction, not the failed one — this is what actually proves
        // the database rolled back, as opposed to merely the Java call throwing.
        Boolean integrationExists = tx.execute(status -> integrationRepository.findById(integrationId).isPresent());
        Boolean auditEventExists = tx.execute(status ->
                !auditEventRepository.findByTargetTypeAndTargetId("INTEGRATION", integrationId).isEmpty());

        assertThat(integrationExists).isFalse();
        assertThat(auditEventExists).isFalse();
    }

    @Test
    void idempotencyKeyCannotBeRecordedTwiceForTheSameTenantScopeAndKey() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID tenantId = tx.execute(status -> tenantRepository.save(
                new Tenant(UUID.randomUUID(), "Acme", "acme-" + UUID.randomUUID(), TenantStatus.ACTIVE)).getId());

        tx.executeWithoutResult(status ->
                idempotencyKeyService.record(tenantId, "CREATE_INGESTION", "key-1", "fp-1", 202, "{}"));

        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                idempotencyKeyService.record(tenantId, "CREATE_INGESTION", "key-1", "fp-2", 202, "{}")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
