package ch.swissqcommerce.backend.config;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

public class TelemetrySchemaRegistryTest {

    private TelemetrySchemaRegistry registry;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        registry = new TelemetrySchemaRegistry(objectMapper, new DefaultResourceLoader());
        registry.init();
    }

    @Test
    void testValidSecurityAuditSchema() {
        String payload =
                "{"
                        + "\"action\":\"admin.chaos.inject\","
                        + "\"method\":\"injectFault\","
                        + "\"operator\":\"swissadmin\","
                        + "\"timestamp\":\"2026-06-04T12:00:00Z\","
                        + "\"status\":\"SUCCESS\","
                        + "\"parameters\":{}"
                        + "}";
        assertDoesNotThrow(() -> registry.validate("admin.chaos.inject", payload));
    }

    @Test
    void testValidSecurityAnomalySchema() {
        String payload =
                "{"
                        + "\"action\":\"security.anomaly\","
                        + "\"method\":\"injectFault\","
                        + "\"operator\":\"swissadmin\","
                        + "\"timestamp\":\"2026-06-04T12:00:00Z\","
                        + "\"status\":\"FAILED\","
                        + "\"error\":\"Something went wrong\""
                        + "}";
        assertDoesNotThrow(() -> registry.validate("security.anomaly", payload));
    }

    @Test
    void testSecurityAuditSchemaMissingRequiredField() {
        // Missing "status"
        String payload =
                "{"
                        + "\"action\":\"admin.chaos.inject\","
                        + "\"method\":\"injectFault\","
                        + "\"operator\":\"swissadmin\","
                        + "\"timestamp\":\"2026-06-04T12:00:00Z\""
                        + "}";
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> registry.validate("admin.chaos.inject", payload));
        assertTrue(ex.getMessage().contains("Missing required field 'status'"));
    }

    @Test
    void testSecurityAnomalySchemaMissingRequiredField() {
        // Missing "error"
        String payload =
                "{"
                        + "\"action\":\"security.anomaly\","
                        + "\"method\":\"injectFault\","
                        + "\"operator\":\"swissadmin\","
                        + "\"timestamp\":\"2026-06-04T12:00:00Z\","
                        + "\"status\":\"FAILED\""
                        + "}";
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> registry.validate("security.anomaly", payload));
        assertTrue(ex.getMessage().contains("Missing required field 'error'"));
    }

    @Test
    void testSecurityAnomalySchemaInvalidStatus() {
        // Status must be FAILED, but setting to SUCCESS
        String payload =
                "{"
                        + "\"action\":\"security.anomaly\","
                        + "\"method\":\"injectFault\","
                        + "\"operator\":\"swissadmin\","
                        + "\"timestamp\":\"2026-06-04T12:00:00Z\","
                        + "\"status\":\"SUCCESS\","
                        + "\"error\":\"Something went wrong\""
                        + "}";
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> registry.validate("security.anomaly", payload));
        assertTrue(ex.getMessage().contains("is not in allowed enum list"));
    }

    @Test
    void testInvalidJsonPayload() {
        String payload = "not a valid json";
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> registry.validate("security.anomaly", payload));
        assertTrue(ex.getMessage().contains("Payload is not valid JSON"));
    }

    @Test
    void testCumulativeValidationErrors() {
        // Missing action, operator, and status all at once
        String payload =
                "{"
                        + "\"method\":\"injectFault\","
                        + "\"timestamp\":\"2026-06-04T12:00:00Z\""
                        + "}";
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> registry.validate("admin.chaos.inject", payload));
        String msg = ex.getMessage();
        assertTrue(msg.contains("Missing required field 'action'"));
        assertTrue(msg.contains("Missing required field 'operator'"));
        assertTrue(msg.contains("Missing required field 'status'"));
    }

    @Test
    void testSchemaDisallowsAdditionalProperties() {
        // Includes an extra field "unauthorized_prop"
        String payload =
                "{"
                        + "\"action\":\"admin.chaos.inject\","
                        + "\"method\":\"injectFault\","
                        + "\"operator\":\"swissadmin\","
                        + "\"timestamp\":\"2026-06-04T12:00:00Z\","
                        + "\"status\":\"SUCCESS\","
                        + "\"unauthorized_prop\":\"some value\""
                        + "}";
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> registry.validate("admin.chaos.inject", payload));
        assertTrue(
                ex.getMessage()
                        .contains(
                                "Undeclared additional property 'unauthorized_prop' is not"
                                        + " allowed"));
    }

    @Test
    void testInvalidDateTimeFormat() {
        // Timestamp is not ISO-8601 formatted
        String payload =
                "{"
                        + "\"action\":\"admin.chaos.inject\","
                        + "\"method\":\"injectFault\","
                        + "\"operator\":\"swissadmin\","
                        + "\"timestamp\":\"invalid-date-format\","
                        + "\"status\":\"SUCCESS\""
                        + "}";
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> registry.validate("admin.chaos.inject", payload));
        assertTrue(ex.getMessage().contains("is not a valid ISO-8601 date-time string"));
    }
}
