package com.nexus.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.NexusWorkerApplication;
import com.nexus.ingestion.IngestionJob;
import com.nexus.ingestion.IngestionJobRepository;
import com.nexus.ingestion.IngestionJobStatus;
import com.nexus.ingestion.SourceRecord;
import com.nexus.ingestion.SourceRecordRepository;
import com.nexus.integration.Integration;
import com.nexus.integration.IntegrationConnectionType;
import com.nexus.integration.IntegrationRepository;
import com.nexus.integration.IntegrationStatus;
import com.nexus.reconciliation.ReconciliationRun;
import com.nexus.reconciliation.ReconciliationRunRepository;
import com.nexus.reconciliation.ReconciliationRunStatus;
import com.nexus.tenant.Tenant;
import com.nexus.tenant.TenantMembership;
import com.nexus.tenant.TenantMembershipRepository;
import com.nexus.tenant.TenantRepository;
import com.nexus.tenant.TenantStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Exercises the constraints declared in the Flyway migrations directly —
 * every assertion here fails if a constraint is removed from the SQL even
 * if all Java-level validation is left untouched, which is the point: these
 * invariants hold regardless of application code (CLAUDE.md rule 6).
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = NexusWorkerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConstraintsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private TenantMembershipRepository tenantMembershipRepository;
    @Autowired
    private IntegrationRepository integrationRepository;
    @Autowired
    private IngestionJobRepository ingestionJobRepository;
    @Autowired
    private SourceRecordRepository sourceRecordRepository;
    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Tenant newTenant() {
        return tenantRepository.saveAndFlush(
                new Tenant(UUID.randomUUID(), "Acme", "acme-" + UUID.randomUUID(), TenantStatus.ACTIVE));
    }

    private Integration newIntegration(UUID tenantId) {
        return integrationRepository.saveAndFlush(new Integration(
                UUID.randomUUID(), tenantId, "Acme CRM", "CRM", IntegrationConnectionType.FILE_UPLOAD,
                "{}", IntegrationStatus.ACTIVE));
    }

    @Test
    @Transactional
    void tenantSlugMustBeUnique() {
        Tenant first = newTenant();
        Tenant duplicate = new Tenant(UUID.randomUUID(), "Acme Duplicate", first.getSlug(), TenantStatus.ACTIVE);

        assertThatThrownBy(() -> tenantRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void tenantMembershipIsUniquePerTenantAndPrincipal() {
        Tenant tenant = newTenant();
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(UUID.randomUUID(), tenant.getId(), "user-1", com.nexus.tenant.MembershipRole.OPERATOR));
        TenantMembership duplicate = new TenantMembership(
                UUID.randomUUID(), tenant.getId(), "user-1", com.nexus.tenant.MembershipRole.READ_ONLY);

        assertThatThrownBy(() -> tenantMembershipRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void ingestionJobIdempotencyKeyIsUniquePerTenantAndIntegration() {
        Tenant tenant = newTenant();
        Integration integration = newIntegration(tenant.getId());
        ingestionJobRepository.saveAndFlush(new IngestionJob(
                UUID.randomUUID(), tenant.getId(), integration.getId(), IngestionJobStatus.CREATED, "retry-key-1"));
        IngestionJob duplicate = new IngestionJob(
                UUID.randomUUID(), tenant.getId(), integration.getId(), IngestionJobStatus.CREATED, "retry-key-1");

        assertThatThrownBy(() -> ingestionJobRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void anIngestionJobCannotReferenceAnIntegrationBelongingToADifferentTenant() {
        Tenant tenantA = newTenant();
        Tenant tenantB = newTenant();
        Integration integrationOwnedByTenantB = newIntegration(tenantB.getId());

        // Claims tenantA, but points at an integration that actually belongs to tenantB —
        // exactly the cross-tenant inconsistency the composite FK exists to catch
        // (data-model.md Invariant #2, resolved in V4__create_ingestion_jobs.sql).
        IngestionJob crossTenantJob = new IngestionJob(
                UUID.randomUUID(), tenantA.getId(), integrationOwnedByTenantB.getId(),
                IngestionJobStatus.CREATED, "some-key");

        assertThatThrownBy(() -> ingestionJobRepository.saveAndFlush(crossTenantJob))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void sourceRecordExternalIdIsUniquePerTenantIntegrationAndSourceSystemButDuplicateBusinessKeysAreAllowed() {
        Tenant tenant = newTenant();
        Integration integration = newIntegration(tenant.getId());
        IngestionJob job = ingestionJobRepository.saveAndFlush(new IngestionJob(
                UUID.randomUUID(), tenant.getId(), integration.getId(), IngestionJobStatus.PROCESSING, "k1"));

        sourceRecordRepository.saveAndFlush(new SourceRecord(
                UUID.randomUUID(), tenant.getId(), integration.getId(), job.getId(), "CRM",
                "ORD-1", "{}", "ext-1"));

        // Same business key, different external_record_id — allowed; duplicate business keys
        // within one source are a detectable reconciliation discrepancy, not a DB violation.
        SourceRecord sameBusinessKeyDifferentExternalId = sourceRecordRepository.saveAndFlush(new SourceRecord(
                UUID.randomUUID(), tenant.getId(), integration.getId(), job.getId(), "CRM",
                "ORD-1", "{}", "ext-2"));
        assertThat(sameBusinessKeyDifferentExternalId.getId()).isNotNull();

        // Same external_record_id again — rejected.
        SourceRecord duplicateExternalId = new SourceRecord(
                UUID.randomUUID(), tenant.getId(), integration.getId(), job.getId(), "CRM",
                "ORD-2", "{}", "ext-1");
        assertThatThrownBy(() -> sourceRecordRepository.saveAndFlush(duplicateExternalId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void reconciliationRunCannotCompareAnIntegrationAgainstItself() {
        Tenant tenant = newTenant();
        Integration integration = newIntegration(tenant.getId());
        ReconciliationRun selfComparison = new ReconciliationRun(
                UUID.randomUUID(), tenant.getId(), integration.getId(), integration.getId(),
                ReconciliationRunStatus.RUNNING, "{}");

        assertThatThrownBy(() -> reconciliationRunRepository.saveAndFlush(selfComparison))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void deletingATenantWithExistingChildrenIsRejectedNotCascaded() {
        Tenant tenant = newTenant();
        newIntegration(tenant.getId());

        // No ON DELETE CASCADE anywhere in this schema — deletion of business/audit data is a
        // deliberate, explicit operation, never a side effect of deleting the parent tenant
        // (data-model.md #6 retention philosophy).
        assertThatThrownBy(() -> {
            tenantRepository.delete(tenant);
            tenantRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseLevelCheckConstraintRejectsAnInvalidStatusEvenBypassingJavaEnumSafety() {
        // Deliberately goes around the Java enum type-safety net with a raw SQL insert, to prove
        // the status CHECK constraint is enforced BY POSTGRES, not only by Java validation
        // (the explicit requirement behind every CHECK in these migrations).
        UUID tenantId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO tenants (id, name, slug, status) VALUES (?, ?, ?, ?)",
                tenantId, "Bad Tenant", "bad-" + tenantId, "NOT_A_REAL_STATUS"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
