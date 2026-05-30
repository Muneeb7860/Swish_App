package ch.swissqcommerce.backend.domain.agent.core.service;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentToolExecutor {

    private final OrderRepository orderRepository;

    public String executeTool(String toolName, String argument) {
        if ("ORDER_STATUS".equalsIgnoreCase(toolName)) {
            if (argument == null || argument.trim().isEmpty()) {
                return "Error: No order or customer identifier provided.";
            }
            try {
                int orderId = Integer.parseInt(argument.trim());
                Optional<Order> orderOpt = orderRepository.findById(orderId);
                if (orderOpt.isPresent()) {
                    Order o = orderOpt.get();
                    return "Order ID: " + o.getOrderId() + ", Status: " + o.getStatus() + 
                           ", Total Amount: " + o.getTotalAmount() + ", Created At: " + o.getCreatedAt();
                }
            } catch (NumberFormatException e) {
                List<Order> orders = orderRepository.findByCustomerCustomerIdOrderByCreatedAtDesc(argument.trim());
                if (orders.isEmpty()) {
                    return "No orders found for customer: " + argument;
                }
                return orders.stream()
                        .map(o -> "Order ID: " + o.getOrderId() + ", Status: " + o.getStatus() + ", Total Amount: " + o.getTotalAmount())
                        .collect(Collectors.joining("; "));
            }
            return "Order not found.";
        }
        return "Unknown tool: " + toolName;
    }
}
