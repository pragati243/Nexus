package com.nexus.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.NexusWorkerApplication;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the migrations as a set: correct count and order, clean apply on
 * an empty database, and idempotent on a second run — not just that "the
 * app happens to start." Updated in Phase 3 to include V13 (the
 * READ_ONLY role rename) alongside Phase 2's original twelve.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = NexusWorkerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FlywayMigrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void connectsToRealPostgresNotAnInMemoryFallback() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
    }

    @Test
    void appliesExactlyTheThirteenDocumentedMigrationsInOrder() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).extracting(m -> m.getVersion().toString())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");
        assertThat(applied).extracting(MigrationInfo::getDescription)
                .containsExactly(
                        "create tenants",
                        "create tenant memberships",
                        "create integrations",
                        "create ingestion jobs",
                        "create source records",
                        "create reconciliation runs",
                        "create discrepancies",
                        "create discrepancy events",
                        "create idempotency keys",
                        "create outbox events",
                        "create processing attempts",
                        "create audit events",
                        "rename viewer role to read only");
    }

    @Test
    void reRunningMigrateOnAnAlreadyMigratedSchemaIsANoOp() {
        var result = flyway.migrate();

        assertThat(result.migrationsExecuted).isZero();
    }
}
