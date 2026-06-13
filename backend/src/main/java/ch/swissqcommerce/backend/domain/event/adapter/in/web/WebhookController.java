package ch.swissqcommerce.backend.domain.event.adapter.in.web;

import ch.swissqcommerce.backend.domain.event.adapter.out.persistence.DomainEventEntity;
import ch.swissqcommerce.backend.domain.event.port.in.EventUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook receiver for external automation platforms (n8n, Zapier, etc.).
 *
 * <p>Accepts callback payloads from n8n workflows and routes them into the event pipeline via
 * {@link EventUseCase}. Authentication is handled via HMAC-SHA256 signature verification using a
 * shared secret, since webhook callbacks originate from automation engines — not user sessions.
 *
 * <p>The webhook endpoint is added to the Spring Security {@code permitAll} list because it uses
 * its own HMAC authentication scheme independent of JWT.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final EventUseCase eventUseCase;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;
    private final Counter webhookReceived;
    private final Counter webhookRejected;

    public WebhookController(
            EventUseCase eventUseCase,
            ObjectMapper objectMapper,
            @Value("${swish.webhook.secret:dev-webhook-secret-change-me}") String webhookSecret,
            MeterRegistry meterRegistry) {
        this.eventUseCase = eventUseCase;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
        this.webhookReceived =
                Counter.builder("webhook_events_received_total")
                        .description("Total webhook events received")
                        .register(meterRegistry);
        this.webhookRejected =
                Counter.builder("webhook_events_rejected_total")
                        .description("Total webhook events rejected (auth failure)")
                        .register(meterRegistry);
    }

    /**
     * Receives n8n workflow callback payloads.
     *
     * <p>Expected JSON body:
     *
     * <pre>{
     *   "eventType": "order.placed",
     *   "aggregateId": "order-123",
     *   "payload": { ... }
     * }</pre>
     *
     * @param signature HMAC-SHA256 signature of the request body
     * @param body raw JSON request body
     * @return 200 on success, 401 on invalid/missing signature, 400 on malformed body
     */
    @PostMapping("/n8n")
    public ResponseEntity<Map<String, String>> receiveN8nWebhook(
            @RequestHeader(name = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String body) {
        // 1. Validate HMAC signature
        if (signature == null || signature.isBlank()) {
            webhookRejected.increment();
            log.warn(
                    "WebhookController: Rejected n8n webhook — missing X-Webhook-Signature"
                            + " header.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing X-Webhook-Signature header"));
        }

        if (!verifyHmac(body, signature)) {
            webhookRejected.increment();
            log.warn("WebhookController: Rejected n8n webhook — invalid HMAC signature.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid webhook signature"));
        }

        // 2. Parse and validate request body
        try {
            JsonNode rootNode = objectMapper.readTree(body);
            String eventType = rootNode.path("eventType").asText(null);
            String aggregateId = rootNode.path("aggregateId").asText(null);
            JsonNode payloadNode = rootNode.path("payload");

            if (eventType == null || eventType.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing required field: eventType"));
            }

            String payloadJson =
                    payloadNode.isMissingNode()
                            ? "{}"
                            : objectMapper.writeValueAsString(payloadNode);

            // 3. Persist as domain event through the event pipeline
            DomainEventEntity savedEvent = eventUseCase.publishEvent(eventType, payloadJson);
            webhookReceived.increment();

            log.info(
                    "WebhookController: Accepted n8n webhook — eventType={}, aggregateId={},"
                            + " savedEventId={}",
                    eventType,
                    aggregateId,
                    savedEvent.getEventId());

            return ResponseEntity.ok(
                    Map.of("status", "accepted", "eventId", savedEvent.getEventId()));

        } catch (Exception e) {
            log.error(
                    "WebhookController: Failed to process n8n webhook payload: {}",
                    e.getMessage(),
                    e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid webhook payload: " + e.getMessage()));
        }
    }

    /** Verifies the HMAC-SHA256 signature of the request body. */
    public boolean verifyHmac(String body, String providedSignature) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec =
                    new SecretKeySpec(
                            webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(hash);
            return expectedSignature.equalsIgnoreCase(providedSignature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("WebhookController: HMAC verification error: {}", e.getMessage(), e);
            return false;
        }
    }
}
