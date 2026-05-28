package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "dark_stores", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DarkStore {

    @Id
    @Column(name = "store_id", length = 50)
    @Size(max = 50)
    private String storeId;

    @Column(name = "store_name", length = 100, nullable = false)
    @NotBlank
    @Size(max = 100)
    private String storeName;

    @Column(name = "address", nullable = false, columnDefinition = "TEXT")
    @NotBlank
    private String address;

    @Column(name = "latitude", precision = 9, scale = 6, nullable = false)
    @NotNull
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6, nullable = false)
    @NotNull
    private BigDecimal longitude;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
