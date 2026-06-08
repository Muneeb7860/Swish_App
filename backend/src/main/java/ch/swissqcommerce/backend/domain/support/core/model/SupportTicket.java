package ch.swissqcommerce.backend.domain.support.core.model;

public class SupportTicket {
    private String ticketId;
    private String customerId;
    private String orderId;
    private String priority;
    private String status;

    public SupportTicket() {}

    public SupportTicket(String ticketId, String customerId, String orderId, String priority, String status) {
        this.ticketId = ticketId;
        this.customerId = customerId;
        this.orderId = orderId;
        this.priority = priority;
        this.status = status;
    }

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public void escalate() { this.priority = "HIGH"; }
    public void resolve() { this.status = "RESOLVED"; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String ticketId;
        private String customerId;
        private String orderId;
        private String priority;
        private String status;

        public Builder ticketId(String ticketId) { this.ticketId = ticketId; return this; }
        public Builder customerId(String customerId) { this.customerId = customerId; return this; }
        public Builder orderId(String orderId) { this.orderId = orderId; return this; }
        public Builder priority(String priority) { this.priority = priority; return this; }
        public Builder status(String status) { this.status = status; return this; }

        public SupportTicket build() {
            return new SupportTicket(ticketId, customerId, orderId, priority, status);
        }
    }
}
