package com.nexus.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * The production-shaped JWT decoder: real OIDC provider, real signature
 * verification against its published JWK set, plus explicit issuer,
 * audience and expiry/not-before validation. Active whenever neither
 * {@code local} nor {@code test} is an active profile — including when
 * no profile is set at all, so the safe default is "require real OIDC
 * config," not "fall back to something permissive" (see
 * {@link com.nexus.security.devauth.DevJwtAuthConfig} for the mutually
 * exclusive opposite case).
 *
 * <h2>Configuring a real OIDC provider</h2>
 * Set three environment variables (see {@code .env.example}):
 * <ul>
 *   <li>{@code OIDC_ISSUER_URI} — e.g. {@code https://your-tenant.auth0.com/},
 *       {@code https://login.microsoftonline.com/<tenant-id>/v2.0}, or your
 *       Keycloak realm URL. Compared against every token's {@code iss} claim.</li>
 *   <li>{@code OIDC_JWK_SET_URI} — the provider's published JWK set, usually
 *       {@code <issuer>/.well-known/jwks.json} or discoverable from
 *       {@code <issuer>/.well-known/openid-configuration}. Used to verify
 *       the token signature; fetched lazily (not at startup) so a
 *       misconfigured or unreachable issuer doesn't block application
 *       boot.</li>
 *   <li>{@code OIDC_AUDIENCE} — the audience/client identifier your provider
 *       issues tokens for (e.g. an Auth0 API identifier, an Azure AD
 *       Application ID URI). Compared against every token's {@code aud}
 *       claim; Spring Security has no built-in audience validator, so this
 *       is implemented explicitly below.</li>
 * </ul>
 * No signing key, identity provider, or credential is invented anywhere in
 * this codebase — the three values above are the only things a real
 * deployment needs to supply.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Profile("!local & !test")
public class OidcJwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${nexus.security.oidc.audience}") String audience) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuerUri),
                new AudienceValidator(audience));
        decoder.setJwtValidator(validator);

        return decoder;
    }

    /**
     * Spring Security validates {@code exp}/{@code nbf} and {@code iss} out
     * of the box ({@link JwtTimestampValidator}, {@link JwtIssuerValidator})
     * but ships no audience validator — this is the standard documented
     * pattern for adding one.
     */
    private static final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

        private final String requiredAudience;

        private AudienceValidator(String requiredAudience) {
            this.requiredAudience = requiredAudience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            if (token.getAudience() != null && token.getAudience().contains(requiredAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "The required audience is missing", null));
        }
    }
}
