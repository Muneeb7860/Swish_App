package ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "region_pref", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegionPref {

    @Id
    @Column(name = "zip_prefix", length = 5, nullable = false)
    private String zipPrefix;

    @Column(name = "primary_warehouse_id", length = 50, nullable = false)
    private String primaryWarehouseId;

    @Column(name = "secondary_warehouse_id", length = 50)
    private String secondaryWarehouseId;
}
