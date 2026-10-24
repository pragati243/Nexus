package com.nexus.security.devauth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.annotation.PostConstruct;
import java.security.NoSuchAlgorithmException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The isolated local/test authentication mechanism. No real identity
 * provider, no invented-but-real-looking signing key: the HMAC secret below
 * is generated fresh, in memory, every time this bean is created, and never
 * persisted — a token minted by one JVM run is worthless to any other.
 *
 * <p><b>Why this can't be enabled accidentally in production:</b>
 * <ol>
 *   <li>{@code @Profile({"local", "test"})} — Spring never even constructs
 *       this class unless one of those two profiles is explicitly in
 *       {@code spring.profiles.active}. There is no property default that
 *       turns it on; it requires a deliberate deployment choice.</li>
 *   <li>The {@link #rejectIfProdProfileAlsoActive} check below is a second,
 *       independent guard against the specific mistake of activating
 *       {@code local} or {@code test} *together with* {@code prod} (e.g. a
 *       copy-pasted {@code SPRING_PROFILES_ACTIVE=prod,local}) — it fails
 *       application startup outright rather than silently running with a
 *       forgeable auth path in what's supposed to be a production
 *       environment.</li>
 * </ol>
 * See {@link com.nexus.security.OidcJwtDecoderConfig} for the mutually
 * exclusive production path, and
 * {@link com.nexus.security.devauth.DevTokenController} for how a human
 * gets a token locally.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Profile({"local", "test"})
public class DevJwtAuthConfig {

    private final Environment environment;

    public DevJwtAuthConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void rejectIfProdProfileAlsoActive() {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "Refusing to start: the dev/test JWT auth bypass (profile 'local' or 'test') is active "
                            + "alongside profile 'prod'. This is almost certainly a deployment configuration "
                            + "mistake — fix the active profile list rather than suppressing this check.");
        }
    }

    @Bean
    public SecretKey devJwtSigningKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
        keyGenerator.init(256);
        return keyGenerator.generateKey();
    }

    /**
     * Validates signature and {@code exp}/{@code nbf} (Spring Security's
     * default validator for a bare secret-key decoder) — deliberately no
     * issuer/audience check here, since there is no real issuer to check
     * against; that's the whole point of this being the test path.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey devJwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(devJwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey devJwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(devJwtSigningKey));
    }
}
