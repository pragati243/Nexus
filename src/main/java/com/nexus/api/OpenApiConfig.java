package com.nexus.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OpenApiConfig {

    @Bean
    public OpenAPI nexusOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Nexus API")
                .description("Enterprise Data Integration & Reconciliation Platform")
                .version("v1"));
    }
}
