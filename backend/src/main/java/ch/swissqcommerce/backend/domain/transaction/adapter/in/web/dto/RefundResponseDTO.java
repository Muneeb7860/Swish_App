package ch.swissqcommerce.backend.domain.transaction.adapter.in.web.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class RefundResponseDTO {
    private String status;
    private String message;
    private String ticketId;
}
