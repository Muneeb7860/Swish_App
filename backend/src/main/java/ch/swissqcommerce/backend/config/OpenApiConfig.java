package ch.swissqcommerce.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Swiss Quick Commerce BFF API",
                        version = "1.1.0",
                        description =
                                """
            Backend-for-Frontend (BFF) API Gateway contract for the Swiss Quick Commerce System.
            Integrates the five-sided marketplace: Customers, Riders, Pickers, Wholesalers, and Platform Admins.
            Enforces JWT security with MFA parameters, GDPR compliance controls, and Chaos Engineering fault logs.
            Idempotency-Key headers prevent duplicate execution on critical payment/write routes.
            """,
                        contact =
                                @Contact(
                                        name = "SwissQ Commerce Engineering",
                                        email = "dev@swissqcommerce.ch"),
                        license = @License(name = "Proprietary")),
        servers = {
            @Server(url = "http://localhost:8080", description = "Local Development"),
            @Server(url = "https://api.staging.swissqcommerce.ch", description = "Staging")
        })
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER,
        description = "JWT token obtained from POST /api/v1/auth/login")
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components());
    }

    /** Customer-facing API group */
    @Bean
    public GroupedOpenApi customerApi() {
        return GroupedOpenApi.builder()
                .group("1-customer")
                .displayName("Customer APIs")
                .pathsToMatch(
                        "/api/v1/auth/**",
                        "/api/customer/**",
                        "/api/orders/**",
                        "/api/payments/**",
                        "/api/ledger/**")
                .build();
    }

    /** Rider & Dispatch group */
    @Bean
    public GroupedOpenApi riderApi() {
        return GroupedOpenApi.builder()
                .group("2-rider")
                .displayName("Rider & Dispatch APIs")
                .pathsToMatch("/api/rider/**", "/api/dispatch/**", "/api/telemetry/**")
                .build();
    }

    /** Inventory & Picker group */
    @Bean
    public GroupedOpenApi inventoryApi() {
        return GroupedOpenApi.builder()
                .group("3-inventory")
                .displayName("Inventory & Picker APIs")
                .pathsToMatch("/api/inventory/**", "/api/v1/inventory/**")
                .build();
    }

    /** Wholesaler group */
    @Bean
    public GroupedOpenApi wholesalerApi() {
        return GroupedOpenApi.builder()
                .group("4-wholesaler")
                .displayName("Wholesaler APIs")
                .pathsToMatch("/api/wholesaler/**")
                .build();
    }

    /** Admin, Governance & Security group */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("5-admin")
                .displayName("Admin, Governance & Security APIs")
                .pathsToMatch(
                        "/api/admin/**", "/api/security/**", "/api/governance/**", "/api/agent/**")
                .build();
    }

    /** Actuator group (health, metrics, prometheus) */
    @Bean
    public GroupedOpenApi actuatorApi() {
        return GroupedOpenApi.builder()
                .group("6-actuator")
                .displayName("Actuator & Observability")
                .pathsToMatch("/actuator/**")
                .build();
    }
}
