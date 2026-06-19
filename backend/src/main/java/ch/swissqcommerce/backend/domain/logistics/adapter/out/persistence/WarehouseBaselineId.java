package ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence;

import java.io.Serializable;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseBaselineId implements Serializable {
    private String zipPrefix;
    private String warehouseId;
}
