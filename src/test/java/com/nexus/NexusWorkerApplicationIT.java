package com.nexus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the worker's half of the "two entry points, one codebase" shape
 * (see docs/architecture.md § 2) actually boots — no web server, same
 * shared JPA/DataSource auto-configuration as the API, which is why this
 * still needs a real database even though the worker does nothing with it
 * yet.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = NexusWorkerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NexusWorkerApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private ApplicationContext context;

    @Test
    void workerContextLoadsWithoutStartingAWebServer() {
        assertThat(context).isNotNull();
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
    }
}
