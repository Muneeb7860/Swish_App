package ch.swissqcommerce.backend.domain.transaction.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundResponseDTO {
    private String status;
    private String message;
    private String ticketId;
}
