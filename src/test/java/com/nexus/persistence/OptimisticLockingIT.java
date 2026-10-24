package com.nexus.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.NexusWorkerApplication;
import com.nexus.ingestion.IngestionJob;
import com.nexus.ingestion.IngestionJobRepository;
import com.nexus.ingestion.IngestionJobStatus;
import com.nexus.integration.Integration;
import com.nexus.integration.IntegrationConnectionType;
import com.nexus.integration.IntegrationRepository;
import com.nexus.integration.IntegrationStatus;
import com.nexus.tenant.Tenant;
import com.nexus.tenant.TenantRepository;
import com.nexus.tenant.TenantStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Proves the {@code version} column on {@code ingestion_jobs} actually
 * prevents a lost update — the scenario from architecture.md #10: a
 * redelivered SQS message and an operator-triggered retry racing the same
 * job. Each "session" below is its own transaction, committed independently,
 * to simulate two separate callers rather than two statements in one
 * transaction (which optimistic locking doesn't even apply to).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = NexusWorkerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OptimisticLockingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private IntegrationRepository integrationRepository;
    @Autowired
    private IngestionJobRepository ingestionJobRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void secondConcurrentUpdateOnAStaleVersionIsRejected() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        UUID jobId = tx.execute(status -> {
            Tenant tenant = tenantRepository.save(
                    new Tenant(UUID.randomUUID(), "Acme", "acme-" + UUID.randomUUID(), TenantStatus.ACTIVE));
            Integration integration = integrationRepository.save(new Integration(
                    UUID.randomUUID(), tenant.getId(), "Acme CRM", "CRM",
                    IntegrationConnectionType.FILE_UPLOAD, "{}", IntegrationStatus.ACTIVE));
            IngestionJob job = ingestionJobRepository.save(new IngestionJob(
                    UUID.randomUUID(), tenant.getId(), integration.getId(), IngestionJobStatus.QUEUED, "key-1"));
            return job.getId();
        });

        IngestionJob sessionOneCopy = tx.execute(status -> ingestionJobRepository.findById(jobId).orElseThrow());
        IngestionJob sessionTwoCopy = tx.execute(status -> ingestionJobRepository.findById(jobId).orElseThrow());

        tx.executeWithoutResult(status -> {
            sessionOneCopy.setStatus(IngestionJobStatus.PROCESSING);
            ingestionJobRepository.saveAndFlush(sessionOneCopy);
        });

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            sessionTwoCopy.setStatus(IngestionJobStatus.FAILED);
            ingestionJobRepository.saveAndFlush(sessionTwoCopy);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
