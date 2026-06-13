package ch.swissqcommerce.backend.domain.dispatch.core.model;

import java.math.BigDecimal;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleConfig {

    private String vehicleType;

    private BigDecimal maxWeightKg;

    private BigDecimal averageSpeedKmh;
}
