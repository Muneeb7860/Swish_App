package ch.swissqcommerce.backend.domain.agent.core.service;

import ch.swissqcommerce.backend.domain.agent.port.out.AgentOutPort;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentToolExecutor {

    private final AgentOutPort agentOutPort;
    private final DynamicPricingAgent dynamicPricingAgent;

    public static class ToolResult {
        public final String content;
        public final double cost;

        public ToolResult(String content, double cost) {
            this.content = content;
            this.cost = cost;
        }
    }

    public ToolResult executeTool(String toolName, String argument) {
        if (CustomerSupportAgent.TOOL_ORDER_STATUS.equalsIgnoreCase(toolName)) {
            if (argument == null || argument.trim().isEmpty()) {
                return new ToolResult("Error: No order or customer identifier provided.", 0.0);
            }
            try {
                int orderId = Integer.parseInt(argument.trim());
                Optional<Order> orderOpt = agentOutPort.findOrderById(orderId);
                if (orderOpt.isPresent()) {
                    Order o = orderOpt.get();
                    return new ToolResult(
                            "Order ID: "
                                    + o.getOrderId()
                                    + ", Status: "
                                    + o.getStatus()
                                    + ", Total Amount: "
                                    + o.getTotalAmount()
                                    + ", Created At: "
                                    + o.getCreatedAt(),
                            0.0);
                }
            } catch (NumberFormatException e) {
                List<Order> orders = agentOutPort.findOrdersByCustomerId(argument.trim());
                if (orders.isEmpty()) {
                    return new ToolResult("No orders found for customer: " + argument, 0.0);
                }
                String content =
                        orders.stream()
                                .map(
                                        o ->
                                                "Order ID: "
                                                        + o.getOrderId()
                                                        + ", Status: "
                                                        + o.getStatus()
                                                        + ", Total Amount: "
                                                        + o.getTotalAmount())
                                .collect(Collectors.joining("; "));
                return new ToolResult(content, 0.0);
            }
            return new ToolResult("Order not found.", 0.0);
        }

        if (CustomerSupportAgent.TOOL_DYNAMIC_PRICING.equalsIgnoreCase(toolName)) {
            boolean isRaining = false;
            double riderToOrderRatio = 1.0;
            double competitorPrice = 0.0;
            int daysToExpiry = 5;
            double vipDensity = 0.0;

            if (argument != null && !argument.trim().isEmpty()) {
                String[] pairs = argument.split(";");
                for (String pair : pairs) {
                    String[] kv = pair.split("=");
                    if (kv.length == 2) {
                        String key = kv[0].trim().toLowerCase();
                        String val = kv[1].trim();
                        try {
                            if ("raining".equals(key)) {
                                isRaining = Boolean.parseBoolean(val);
                            } else if ("ridertoorderratio".equals(key) || "ratio".equals(key)) {
                                riderToOrderRatio = Double.parseDouble(val);
                            } else if ("competitorprice".equals(key) || "competitor".equals(key)) {
                                competitorPrice = Double.parseDouble(val);
                            } else if ("daystoexpiry".equals(key) || "expiry".equals(key)) {
                                daysToExpiry = Integer.parseInt(val);
                            } else if ("vipdensity".equals(key) || "vip".equals(key)) {
                                vipDensity = Double.parseDouble(val);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            try {
                var analysis =
                        dynamicPricingAgent.recommendPricing(
                                isRaining,
                                riderToOrderRatio,
                                competitorPrice,
                                daysToExpiry,
                                vipDensity);
                String result =
                        String.format(
                                "Dynamic Pricing Recommendation: Surge Multiplier: %.2fx, Discount"
                                        + " Percent: %.2f%%. Rationale: %s",
                                analysis.surgeMultiplier,
                                analysis.discountPercent,
                                analysis.rationale);
                return new ToolResult(result, analysis.tokenCost);
            } catch (Exception e) {
                // Rule-based fallback if execution fails
                double surge = isRaining ? 2.0 : 1.0;
                double discount = (daysToExpiry > 0 && daysToExpiry <= 2) ? 20.0 : 0.0;
                String result =
                        String.format(
                                "Dynamic Pricing Recommendation (Fallback): Surge Multiplier:"
                                    + " %.2fx, Discount Percent: %.2f%%. Rationale: Exception in"
                                    + " pricing agent: %s",
                                surge, discount, e.getMessage());
                return new ToolResult(result, 0.0);
            }
        }

        return new ToolResult("Unknown tool: " + toolName, 0.0);
    }
}
