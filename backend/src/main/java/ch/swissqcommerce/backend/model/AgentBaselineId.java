package ch.swissqcommerce.backend.model;

import java.io.Serializable;
import java.time.LocalDate;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentBaselineId implements Serializable {
    private String sku;
    private LocalDate date;
}
