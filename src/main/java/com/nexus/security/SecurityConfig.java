package com.nexus.security;

import tools.jackson.databind.ObjectMapper;
import com.nexus.api.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Deny-by-default HTTP authorization, established before any protected
 * business endpoint exists (see docs/threat-model.md § 1-2). Only
 * operational/public metadata endpoints are open; everything else requires
 * an authenticated JWT.
 *
 * <p>Restricted to servlet web applications because {@code nexus-worker}
 * boots with {@code WebApplicationType.NONE} and has no {@link HttpSecurity}
 * infrastructure to configure.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/api/v1/system/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()));
        return http.build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> writeProblem(response, request,
                HttpStatus.UNAUTHORIZED, "Unauthorized", "A valid access token is required.");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeProblem(response, request,
                HttpStatus.FORBIDDEN, "Forbidden", "You do not have access to this resource.");
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
