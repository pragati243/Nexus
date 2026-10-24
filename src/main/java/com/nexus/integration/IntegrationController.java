package com.nexus.integration;

import com.nexus.tenant.CurrentTenant;
import com.nexus.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * The vertical slice this phase uses to prove the authorization model end
 * to end — see docs/authorization-model.md. Every method takes
 * {@code @CurrentTenant}, resolved server-side from the authenticated
 * principal's memberships; nothing here ever reads a client-supplied
 * tenant id as proof of anything (CLAUDE.md rule 2).
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations")
public class IntegrationController {

    private final IntegrationService integrationService;
    private final ObjectMapper objectMapper;

    public IntegrationController(IntegrationService integrationService, ObjectMapper objectMapper) {
        this.integrationService = integrationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an integration", description = "Requires role TENANT_ADMIN.")
    public IntegrationResponse create(@CurrentTenant TenantContext context,
            @Valid @RequestBody CreateIntegrationRequest request) {
        String configJson = objectMapper.writeValueAsString(request.config());
        Integration saved = integrationService.create(context, request.name(), request.sourceSystem(),
                request.connectionType(), configJson);
        return IntegrationResponse.from(saved);
    }

    @GetMapping
    @Operation(summary = "List integrations visible to the caller's tenant")
    public IntegrationListResponse list(@CurrentTenant TenantContext context,
            @RequestParam(required = false) IntegrationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return IntegrationListResponse.from(integrationService.list(context, status, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one integration",
            description = "404 if it doesn't exist, or exists in a different tenant — same response either way.")
    public IntegrationResponse getById(@CurrentTenant TenantContext context, @PathVariable UUID id) {
        return IntegrationResponse.from(integrationService.getById(context, id));
    }
}
