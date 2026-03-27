package com.catalog.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Software Catalog Service API",
                version = "1.0",
                description = "REST API for tracking software applications and their releases"
        )
)
public class OpenApiConfig {
}
