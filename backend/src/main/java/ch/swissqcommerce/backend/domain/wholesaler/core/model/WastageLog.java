package ch.swissqcommerce.backend.domain.wholesaler.core.model;
import java.time.OffsetDateTime;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WastageLog {

    private String logId;

    private String storeId;

    private String productId;

    private String batchId;

    private Integer qtyWasted;

    private String reason; // EXPIRED, DAMAGED_IN_STORE, MELTED_COLD_CHAIN

    private String loggedBy;

    private OffsetDateTime timestamp;
}