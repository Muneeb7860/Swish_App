package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "system_configurations", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfiguration {

    @Id
    @Column(name = "config_key", length = 100)
    @Size(max = 100)
    private String configKey;

    @Column(name = "config_value", length = 255, nullable = false)
    @NotBlank
    @Size(max = 255)
    private String configValue;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
