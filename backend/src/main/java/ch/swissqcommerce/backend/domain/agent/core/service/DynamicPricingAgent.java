package ch.swissqcommerce.backend.domain.agent.core.service;

import ch.swissqcommerce.backend.domain.agent.port.out.AgentOutPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmGatewayPort;
import ch.swissqcommerce.backend.domain.agent.port.out.LlmResponse;
import ch.swissqcommerce.backend.model.HitlQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DynamicPricingAgent {

    // Depends only on the port (ADR-001). The @Primary ResilientLlmGateway is injected,
    // which owns the fail-safe fallback chain (ADR-007).
    private final LlmGatewayPort llmGateway;
    private final PricingGuardrailsEngine pricingGuardrailsEngine;
    private final AgentOutPort agentOutPort;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Executor executor;

    @Value("${pricing.agent.sla-timeout-ms:1000}")
    private long slaTimeoutMs;

    public DynamicPricingAgent(
            LlmGatewayPort llmGateway,
            PricingGuardrailsEngine pricingGuardrailsEngine,
            AgentOutPort agentOutPort,
            @Qualifier("engineTaskExecutor") Executor executor) {
        this.llmGateway = llmGateway;
        this.pricingGuardrailsEngine = pricingGuardrailsEngine;
        this.agentOutPort = agentOutPort;
        this.executor = executor;
    }

    public PricingAnalysis recommendPricing(
            boolean isRaining,
            double riderToOrderRatio,
            double competitorPrice,
            int daysToExpiry,
            double vipDensity) {
        String prompt =
                "You are a dynamic pricing agent for Swish OS. Zonal metrics: \n"
                        + "  - Rain status: "
                        + isRaining
                        + "\n"
                        + "  - Rider-to-order ratio: "
                        + riderToOrderRatio
                        + "\n"
                        + "  - Competitor delivery fee: "
                        + competitorPrice
                        + " CHF\n"
                        + "  - Days to product expiration: "
                        + daysToExpiry
                        + "\n"
                        + "  - VIP customer density (0.0 to 1.0): "
                        + vipDensity
                        + "\n"
                        + "Determine: \n"
                        + "  1. Recommended surge multiplier (between 1.0 and 3.0).\n"
                        + "  2. Recommended perishable discount percent (between 0.0 and 50.0).\n"
                        + "  3. Confidence score (0.0 to 1.0).\n"
                        + "  4. Reasoning.\n"
                        + "Response MUST be a valid JSON only, without any markdown formatting"
                        + " block, matching this structure:\n"
                        + "{\n"
                        + "  \"surgeMultiplier\": 1.5,\n"
                        + "  \"discountPercent\": 10.0,\n"
                        + "  \"confidence\": 0.90,\n"
                        + "  \"rationale\": \"explanation\"\n"
                        + "}";

        CompletableFuture<LlmResponse> future =
                CompletableFuture.supplyAsync(() -> llmGateway.callLlm(prompt), executor);

        LlmResponse response = null;
        try {
            response = future.get(slaTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn(
                    "Pricing agent SLA breached (timed out after {}ms). Switching to in-house"
                            + " rules.",
                    slaTimeoutMs);
            return getInHouseFallback(
                    isRaining, riderToOrderRatio, daysToExpiry, "SLA Breached - Timed Out");
        } catch (Exception e) {
            log.error("Pricing agent execution failed. Switching to in-house rules.", e);
            return getInHouseFallback(
                    isRaining,
                    riderToOrderRatio,
                    daysToExpiry,
                    "Execution Error: " + e.getMessage());
        }

        return parseAndValidate(
                response.getContent(),
                response.getTokenCost(),
                isRaining,
                riderToOrderRatio,
                daysToExpiry);
    }

    private PricingAnalysis parseAndValidate(
            String rawContent,
            double cost,
            boolean isRaining,
            double riderToOrderRatio,
            int daysToExpiry) {
        try {
            String json = rawContent.trim();
            if (json.startsWith("```json")) {
                json = json.substring(7);
            }
            if (json.startsWith("```")) {
                json = json.substring(3);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.trim();

            Map<?, ?> map = objectMapper.readValue(json, Map.class);

            double surgeMultiplier =
                    map.get("surgeMultiplier") != null
                            ? Double.parseDouble(map.get("surgeMultiplier").toString())
                            : 1.0;

            double discountPercent =
                    map.get("discountPercent") != null
                            ? Double.parseDouble(map.get("discountPercent").toString())
                            : 0.0;

            double confidence =
                    map.get("confidence") != null
                            ? Double.parseDouble(map.get("confidence").toString())
                            : 0.5;

            String rationale = map.get("rationale") != null ? map.get("rationale").toString() : "";

            // Check if agent is low confidence (< 0.70)
            if (confidence < 0.70) {
                log.warn(
                        "Pricing agent low confidence ({}). Switching to in-house rules.",
                        confidence);
                return getInHouseFallback(
                        isRaining,
                        riderToOrderRatio,
                        daysToExpiry,
                        "Low Confidence (" + confidence + ") - Hallucination Risk");
            }

            // Enforce Guardrails
            PricingGuardrailsEngine.GuardrailResult guardrailResult =
                    pricingGuardrailsEngine.validateAndClamp(surgeMultiplier, discountPercent);

            // Phase 8: aggressive (but in-range) pricing is flagged to the HITL queue
            // for supervisor review while the clamped recommendation still proceeds.
            if (guardrailResult.isNeedsReview()) {
                raisePricingReview(
                        guardrailResult.getReviewReason(), surgeMultiplier, discountPercent);
            }

            return new PricingAnalysis(
                    guardrailResult.getValidatedSurgeMultiplier(),
                    guardrailResult.getValidatedDiscountPercent(),
                    confidence,
                    rationale + " | " + guardrailResult.getMessage(),
                    cost,
                    false // Not a fallback
                    );
        } catch (Exception e) {
            log.warn(
                    "Failed to parse dynamic pricing agent response. Switching to in-house rules.",
                    e);
            return getInHouseFallback(
                    isRaining, riderToOrderRatio, daysToExpiry, "Parsing Error: " + e.getMessage());
        }
    }

    private PricingAnalysis getInHouseFallback(
            boolean isRaining, double riderToOrderRatio, int daysToExpiry, String fallbackReason) {
        double surgeMultiplier = 1.0;
        if (isRaining) {
            surgeMultiplier = 2.0; // Standard rain surge rule
        } else if (riderToOrderRatio < 1.0) {
            surgeMultiplier = 1.5; // Short supply surge rule
        }

        double discountPercent = 0.0;
        if (daysToExpiry > 0 && daysToExpiry <= 2) {
            discountPercent = 20.0; // Near-expiry discount rule
        }

        return new PricingAnalysis(
                surgeMultiplier,
                discountPercent,
                1.0,
                "In-house fallback applied. Reason: " + fallbackReason,
                0.0,
                true // Is a fallback
                );
    }

    /** Best-effort: file a pricing-review ticket to the HITL queue (never breaks pricing). */
    private void raisePricingReview(String reason, double rawSurge, double rawDiscount) {
        try {
            HitlQueue ticket =
                    HitlQueue.builder()
                            .ticketId(
                                    "HITL-PRICE-"
                                            + UUID.randomUUID()
                                                    .toString()
                                                    .substring(0, 8)
                                                    .toUpperCase())
                            .type("pricing_review")
                            .description(
                                    String.format(
                                            "Pricing flagged for review: %s (model proposed"
                                                    + " surge=%.2fx, discount=%.1f%%)",
                                            reason, rawSurge, rawDiscount))
                            .amount(BigDecimal.ZERO)
                            .status("pending")
                            .build();
            agentOutPort.saveHitlQueue(ticket);
            log.info(
                    "DynamicPricingAgent: raised pricing-review HITL ticket {} — {}",
                    ticket.getTicketId(),
                    reason);
        } catch (Exception e) {
            log.warn(
                    "DynamicPricingAgent: failed to file pricing-review HITL ticket (non-fatal):"
                            + " {}",
                    e.getMessage());
        }
    }

    public static class PricingAnalysis {
        public double surgeMultiplier;
        public double discountPercent;
        public double confidence;
        public String rationale;
        public double tokenCost;
        public boolean fallbackApplied;

        public PricingAnalysis(
                double surgeMultiplier,
                double discountPercent,
                double confidence,
                String rationale,
                double tokenCost,
                boolean fallbackApplied) {
            this.surgeMultiplier = surgeMultiplier;
            this.discountPercent = discountPercent;
            this.confidence = confidence;
            this.rationale = rationale;
            this.tokenCost = tokenCost;
            this.fallbackApplied = fallbackApplied;
        }
    }
}
