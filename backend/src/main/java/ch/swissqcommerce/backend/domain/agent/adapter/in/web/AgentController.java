package ch.swissqcommerce.backend.domain.agent.adapter.in.web;

import ch.swissqcommerce.backend.agent.AgentOrchestrator;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentMetrics;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;
import ch.swissqcommerce.backend.domain.agent.port.in.AgentUseCase;
import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired private AgentUseCase agentUseCase;

    @Autowired private AgentOrchestrator agentOrchestrator;

    @Autowired
    private ch.swissqcommerce.backend.domain.agent.core.service.DynamicPricingAgent
            dynamicPricingAgent;

    @PostMapping("/suggest/all")
    public ResponseEntity<Map<String, String>> suggestAll(@RequestBody(required = false) Map<String, String> request) {
        String inputSummary = (request != null && request.containsKey("inputSummary"))
                ? request.get("inputSummary")
                : "Manual trigger";
        
        agentOrchestrator.runOrchestrationAsync(inputSummary);
        
        return ResponseEntity.accepted().body(Map.of(
                "status", "processing",
                "message", "Agent orchestration started asynchronously"
        ));
    }

    @PostMapping("/suggest/debug")
    public ResponseEntity<List<AgentSuggestionEntity>> suggestDebug(@RequestBody(required = false) Map<String, String> request) {
        String inputSummary = (request != null && request.containsKey("inputSummary"))
                ? request.get("inputSummary")
                : "Manual debug trigger";
        
        List<AgentSuggestionEntity> logs = agentOrchestrator.runOrchestrationSync(inputSummary);
        return ResponseEntity.ok(logs);
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody AgentRequest request) {
        AgentResponse response = agentUseCase.processMessage(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<AgentMetrics> getMetrics() {
        return ResponseEntity.ok(agentUseCase.getMetrics());
    }

    @PostMapping("/negotiate")
    public ResponseEntity<AgentUseCase.NegotiationResponse> negotiate(
            @RequestBody AgentUseCase.NegotiationRequest request) {
        AgentUseCase.NegotiationResponse response = agentUseCase.negotiateProcurement(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/price-recommendation")
    public ResponseEntity<PricingRecommendationResponse> getPriceRecommendation(
            @RequestBody PricingRecommendationRequest request) {
        var analysis =
                dynamicPricingAgent.recommendPricing(
                        request.isRaining(),
                        request.getRiderToOrderRatio(),
                        request.getCompetitorPrice(),
                        request.getDaysToExpiry(),
                        request.getVipDensity());

        PricingRecommendationResponse response =
                new PricingRecommendationResponse(
                        analysis.surgeMultiplier,
                        analysis.discountPercent,
                        analysis.confidence,
                        analysis.rationale,
                        analysis.tokenCost,
                        analysis.fallbackApplied);
        return ResponseEntity.ok(response);
    }

    public static class PricingRecommendationRequest {
        private boolean raining;
        private double riderToOrderRatio;
        private double competitorPrice;
        private int daysToExpiry;
        private double vipDensity;

        public boolean isRaining() {
            return raining;
        }

        public void setRaining(boolean raining) {
            this.raining = raining;
        }

        public double getRiderToOrderRatio() {
            return riderToOrderRatio;
        }

        public void setRiderToOrderRatio(double riderToOrderRatio) {
            this.riderToOrderRatio = riderToOrderRatio;
        }

        public double getCompetitorPrice() {
            return competitorPrice;
        }

        public void setCompetitorPrice(double competitorPrice) {
            this.competitorPrice = competitorPrice;
        }

        public int getDaysToExpiry() {
            return daysToExpiry;
        }

        public void setDaysToExpiry(int daysToExpiry) {
            this.daysToExpiry = daysToExpiry;
        }

        public double getVipDensity() {
            return vipDensity;
        }

        public void setVipDensity(double vipDensity) {
            this.vipDensity = vipDensity;
        }
    }

    public static class PricingRecommendationResponse {
        private double surgeMultiplier;
        private double discountPercent;
        private double confidence;
        private String rationale;
        private double tokenCost;
        private boolean fallbackApplied;

        public PricingRecommendationResponse(
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

        public double getSurgeMultiplier() {
            return surgeMultiplier;
        }

        public double getDiscountPercent() {
            return discountPercent;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getRationale() {
            return rationale;
        }

        public double getTokenCost() {
            return tokenCost;
        }

        public boolean isFallbackApplied() {
            return fallbackApplied;
        }
    }
}
