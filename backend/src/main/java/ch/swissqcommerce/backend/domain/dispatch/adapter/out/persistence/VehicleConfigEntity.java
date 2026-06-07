package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "vehicle_configs", schema = "dispatch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleConfigEntity {

    @Id
    @Column(name = "vehicle_type", length = 50)
    @NotBlank
    @Size(max = 50)
    private String vehicleType;

    @Column(name = "max_weight_kg", precision = 5, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal maxWeightKg;

    @Column(name = "average_speed_kmh", precision = 5, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal averageSpeedKmh;
}
