package ch.swissqcommerce.backend.domain.support.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "support_tickets", schema = "oltp")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketEntity {

    @Id
    @Column(name = "ticket_id", length = 50)
    private String ticketId;

    @Column(name = "customer_id", length = 50, nullable = false)
    private String customerId;

    @Column(name = "order_id", length = 50, nullable = false)
    private String orderId;

    @Column(name = "priority", length = 20, nullable = false)
    private String priority;

    @Column(name = "status", length = 30, nullable = false)
    private String status;
}
