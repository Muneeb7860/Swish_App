package ch.swissqcommerce.backend.domain.governance.adapter.in.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HitlTaskResponse {
    private UUID id;
    private UUID traceId;
    private String agentName;
    private String domain;
    private String entityId;
    private Double oldValue;
    private Double newValue;
    private String impact;
    private BigDecimal confidence;
    private String reason;
    private OffsetDateTime expiresAt;
    private String status;
    private OffsetDateTime createdAt;
}
