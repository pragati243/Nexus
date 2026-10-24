package com.nexus.security.devauth;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

/**
 * Mints tokens signed with {@link DevJwtAuthConfig}'s ephemeral key — used
 * directly by integration tests (autowired, no HTTP round trip needed) and
 * indirectly by {@link DevTokenController} for local manual testing.
 * {@code @Profile({"local", "test"})} means this bean plainly does not
 * exist outside those two profiles; {@code @ConditionalOnWebApplication}
 * matches {@link DevJwtAuthConfig}'s own gating so this never tries to
 * construct (and fail, for lack of a {@link JwtEncoder}) in
 * {@code nexus-worker}'s non-web context under the "test" profile.
 */
@Service
@Profile({"local", "test"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TestTokenIssuer {

    private static final String DEV_ISSUER = "https://nexus.local/dev";
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final JwtEncoder jwtEncoder;

    public TestTokenIssuer(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String issue(String subject) {
        return issue(subject, Instant.now(), DEFAULT_TTL);
    }

    public String issueExpired(String subject) {
        Instant issuedInThePast = Instant.now().minus(Duration.ofHours(2));
        return issue(subject, issuedInThePast, Duration.ofMinutes(1));
    }

    private String issue(String subject, Instant issuedAt, Duration ttl) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(DEV_ISSUER)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
        return jwt.getTokenValue();
    }
}
