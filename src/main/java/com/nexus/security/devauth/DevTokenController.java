package com.nexus.security.devauth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-dev convenience only: mints a bearer token for any subject you ask
 * for, no credential check at all. This is exactly as dangerous as it
 * sounds, which is why it only exists when {@code local} or {@code test} is
 * active — see {@link DevJwtAuthConfig} for the guarantees behind that.
 * Never add this path to a shared or internet-reachable environment's
 * configuration. {@code @ConditionalOnWebApplication} matches
 * {@link TestTokenIssuer}'s own gating — see its Javadoc for why.
 */
@RestController
@Profile({"local", "test"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1/dev/tokens")
@Tag(name = "Dev auth (local/test only)", description = "Not present in production builds")
public class DevTokenController {

    private final TestTokenIssuer testTokenIssuer;

    public DevTokenController(TestTokenIssuer testTokenIssuer) {
        this.testTokenIssuer = testTokenIssuer;
    }

    @PostMapping
    @Operation(summary = "Mint a dev-only bearer token",
            description = "No credential check. Only exists when profile 'local' or 'test' is active.")
    public DevTokenResponse issueToken(@Valid @RequestBody DevTokenRequest request) {
        String token = testTokenIssuer.issue(request.subject());
        return new DevTokenResponse(token, "Bearer", 3600);
    }
}
