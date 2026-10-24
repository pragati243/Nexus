package com.nexus.security;

import tools.jackson.databind.ObjectMapper;
import com.nexus.api.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Deny-by-default HTTP authorization, established before any protected
 * business endpoint exists (see docs/threat-model.md § 1-2). Only
 * operational/public metadata endpoints are open; everything else requires
 * an authenticated JWT. Which {@code JwtDecoder} actually validates that
 * JWT is decided elsewhere ({@link OidcJwtDecoderConfig} for production,
 * {@code com.nexus.security.devauth.DevJwtAuthConfig} for local/test) — this
 * class only wires {@code .oauth2ResourceServer().jwt()} to whichever one
 * the active profile provides.
 *
 * <p>Restricted to servlet web applications because {@code nexus-worker}
 * boots with {@code WebApplicationType.NONE} and has no {@link HttpSecurity}
 * infrastructure to configure.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String[] ALWAYS_PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/api/v1/system/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    /**
     * Only ever added to the public-path list when {@code local} or
     * {@code test} is active (see {@link #publicPaths()}) — in any other
     * profile this string simply isn't in the matcher, on top of the
     * controller bean itself not existing (defense in depth, not either/or).
     */
    private static final String DEV_TOKEN_PATH = "/api/v1/dev/tokens";

    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final List<String> corsAllowedOrigins;

    public SecurityConfig(ObjectMapper objectMapper, Environment environment,
            @Value("${nexus.security.cors.allowed-origins:}") List<String> corsAllowedOrigins) {
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(publicPaths()).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()));
        return http.build();
    }

    private String[] publicPaths() {
        List<String> paths = new ArrayList<>(Arrays.asList(ALWAYS_PUBLIC_PATHS));
        if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            paths.add(DEV_TOKEN_PATH);
        }
        return paths.toArray(new String[0]);
    }

    /**
     * Driven entirely by {@code CORS_ALLOWED_ORIGINS} (comma-separated,
     * empty/unset by default). No wildcard origin is ever configured here —
     * an empty list means no cross-origin browser calls are permitted at
     * all, which is the correct default for an API with no first-party
     * frontend origin declared yet. Set the property explicitly per
     * environment; local dev convenience values belong in
     * {@code application-local.yml}, not in this default.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        if (!corsAllowedOrigins.isEmpty()
                && !(corsAllowedOrigins.size() == 1 && corsAllowedOrigins.get(0).isBlank())) {
            configuration.setAllowedOrigins(corsAllowedOrigins);
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key",
                    "X-Tenant-Id", "X-Correlation-Id", "If-Match"));
            configuration.setExposedHeaders(List.of("X-Correlation-Id"));
            configuration.setAllowCredentials(false);
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            log.warn("Authentication failed [path={}, reason={}]", request.getRequestURI(),
                    authException.getClass().getSimpleName());
            writeProblem(response, request, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "A valid access token is required.");
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String principal = authentication != null ? authentication.getName() : "unknown";
            log.warn("Access denied [path={}, principal={}]", request.getRequestURI(), principal);
            writeProblem(response, request, HttpStatus.FORBIDDEN, "Forbidden",
                    "You do not have access to this resource.");
        };
    }

    private void writeProblem(HttpServletResponse response, jakarta.servlet.http.HttpServletRequest request,
            HttpStatus status, String title, String detail) throws java.io.IOException {
        var problemDetail = ProblemDetailFactory.create(status, title, detail,
                ProblemDetailFactory.codeFor(status), request);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
