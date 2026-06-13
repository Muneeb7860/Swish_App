package ch.swissqcommerce.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Optimized and hardened Registry holding event schema definitions and performing deep JSON payload
 * validations with cumulative error diagnostics.
 */
@Component
public class TelemetrySchemaRegistry {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySchemaRegistry.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private final Map<String, JsonNode> schemas = new HashMap<>();

    @Autowired
    public TelemetrySchemaRegistry(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        loadSchema("security-audit", "classpath:schemas/security-audit-schema.json");
        loadSchema("security-anomaly", "classpath:schemas/security-anomaly-schema.json");
    }

    private void loadSchema(String key, String resourcePath) {
        try {
            Resource resource = resourceLoader.getResource(resourcePath);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    JsonNode schemaNode = objectMapper.readTree(is);
                    schemas.put(key, schemaNode);
                    log.info("Successfully loaded schema for key '{}' from {}", key, resourcePath);
                }
            } else {
                log.warn("Schema resource not found: {}", resourcePath);
            }
        } catch (Exception e) {
            log.error(
                    "Failed to load schema '{}' from path '{}': {}",
                    key,
                    resourcePath,
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Validates the payload against the corresponding schema. Throws IllegalArgumentException
     * carrying all cumulative error details if validation fails.
     */
    public void validate(String eventType, String jsonPayload) {
        if (jsonPayload == null || jsonPayload.trim().isEmpty()) {
            throw new IllegalArgumentException("Payload cannot be empty");
        }

        String schemaKey = resolveSchemaKey(eventType);
        JsonNode schema = schemas.get(schemaKey);
        if (schema == null) {
            log.debug(
                    "No registered schema found for eventType '{}'. Skipping validation.",
                    eventType);
            return;
        }

        try {
            JsonNode payloadNode = objectMapper.readTree(jsonPayload);
            List<String> errors = new ArrayList<>();
            validateNode(payloadNode, schema, eventType, errors);

            if (!errors.isEmpty()) {
                String errorSummary = String.join(", ", errors);
                throw new IllegalArgumentException(
                        String.format(
                                "Schema validation failed for eventType '%s': [%s]",
                                eventType, errorSummary));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Payload is not valid JSON: " + e.getMessage(), e);
        }
    }

    private String resolveSchemaKey(String eventType) {
        if ("security.anomaly".equals(eventType)) {
            return "security-anomaly";
        }
        if (eventType != null
                && (eventType.startsWith("admin.") || eventType.startsWith("security."))) {
            return "security-audit";
        }
        return "unknown";
    }

    private void validateNode(
            JsonNode payloadNode, JsonNode schemaNode, String eventType, List<String> errors) {
        // 1. Verify required fields
        JsonNode requiredNode = schemaNode.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            for (JsonNode reqField : requiredNode) {
                String fieldName = reqField.asText();
                if (!payloadNode.has(fieldName) || payloadNode.get(fieldName).isNull()) {
                    errors.add(String.format("Missing required field '%s'", fieldName));
                }
            }
        }

        // 2. Verify properties type and enum constraints
        JsonNode propertiesNode = schemaNode.get("properties");
        if (propertiesNode != null && propertiesNode.isObject()) {
            propertiesNode
                    .fieldNames()
                    .forEachRemaining(
                            fieldName -> {
                                if (payloadNode.has(fieldName)) {
                                    JsonNode valNode = payloadNode.get(fieldName);
                                    if (valNode.isNull()) {
                                        return;
                                    }

                                    JsonNode fieldSchema = propertiesNode.get(fieldName);
                                    String expectedType =
                                            fieldSchema.has("type")
                                                    ? fieldSchema.get("type").asText()
                                                    : null;

                                    if (expectedType != null) {
                                        validateType(
                                                valNode,
                                                expectedType,
                                                fieldName,
                                                eventType,
                                                errors);
                                    }

                                    // Format check (e.g. date-time format)
                                    String format =
                                            fieldSchema.has("format")
                                                    ? fieldSchema.get("format").asText()
                                                    : null;
                                    if ("date-time".equals(format) && valNode.isTextual()) {
                                        validateDateTimeFormat(valNode.asText(), fieldName, errors);
                                    }

                                    // Enum check
                                    JsonNode enumNode = fieldSchema.get("enum");
                                    if (enumNode != null && enumNode.isArray()) {
                                        boolean match = false;
                                        String actualValue = valNode.asText();
                                        for (JsonNode enumVal : enumNode) {
                                            if (enumVal.asText().equals(actualValue)) {
                                                match = true;
                                                break;
                                            }
                                        }
                                        if (!match) {
                                            errors.add(
                                                    String.format(
                                                            "Field '%s' value '%s' is not in"
                                                                    + " allowed enum list",
                                                            fieldName, actualValue));
                                        }
                                    }
                                }
                            });
        }

        // 3. Strict Additional Properties Constraint (additionalProperties: false)
        JsonNode additionalPropsNode = schemaNode.get("additionalProperties");
        boolean additionalPropertiesAllowed =
                additionalPropsNode == null || additionalPropsNode.asBoolean();
        if (!additionalPropertiesAllowed && propertiesNode != null) {
            payloadNode
                    .fieldNames()
                    .forEachRemaining(
                            payloadField -> {
                                if (!propertiesNode.has(payloadField)) {
                                    errors.add(
                                            String.format(
                                                    "Undeclared additional property '%s' is not"
                                                            + " allowed",
                                                    payloadField));
                                }
                            });
        }
    }

    private void validateType(
            JsonNode node,
            String expectedType,
            String fieldName,
            String eventType,
            List<String> errors) {
        boolean valid = false;
        switch (expectedType) {
            case "string":
                valid = node.isTextual();
                break;
            case "object":
                valid = node.isObject();
                break;
            case "array":
                valid = node.isArray();
                break;
            case "boolean":
                valid = node.isBoolean();
                break;
            case "number":
                valid = node.isNumber();
                break;
            case "integer":
                valid = node.isIntegralNumber();
                break;
            default:
                valid = true; // Skip unknown types
                break;
        }

        if (!valid) {
            errors.add(
                    String.format(
                            "Field '%s' expected type '%s', but got '%s'",
                            fieldName, expectedType, node.getNodeType().toString().toLowerCase()));
        }
    }

    private void validateDateTimeFormat(String value, String fieldName, List<String> errors) {
        try {
            DateTimeFormatter.ISO_DATE_TIME.parse(value);
        } catch (DateTimeParseException e) {
            errors.add(
                    String.format(
                            "Field '%s' value '%s' is not a valid ISO-8601 date-time string",
                            fieldName, value));
        }
    }
}
