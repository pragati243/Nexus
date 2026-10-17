package com.nexus.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public service metadata — deliberately unauthenticated (see
 * {@code com.nexus.security.SecurityConfig}) so it can be used as a basic
 * liveness/identity check by tooling that doesn't carry a JWT.
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Service metadata")
public class SystemInfoController {

    private final BuildProperties buildProperties;
    private final Environment environment;

    public SystemInfoController(ObjectProvider<BuildProperties> buildProperties, Environment environment) {
        this.buildProperties = buildProperties.getIfAvailable();
        this.environment = environment;
    }

    @GetMapping("/info")
    @Operation(summary = "Service metadata", description = "Application name, version, build time, active profiles, and runtime info.")
    public SystemInfoResponse info() {
        String[] activeProfiles = environment.getActiveProfiles();
        return new SystemInfoResponse(
                buildProperties != null ? buildProperties.getName() : "nexus",
                buildProperties != null ? buildProperties.getVersion() : "unknown",
                buildProperties != null ? buildProperties.getTime() : null,
                activeProfiles.length > 0 ? List.of(activeProfiles) : List.of("default"),
                Runtime.version().toString(),
                Instant.now());
    }
}
