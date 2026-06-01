package ch.swissqcommerce.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "SwissQ Commerce API",
        version = "1.0",
        description = "API Documentation for the SwissQ Commerce Backend"
    )
)
public class OpenApiConfig {
}
