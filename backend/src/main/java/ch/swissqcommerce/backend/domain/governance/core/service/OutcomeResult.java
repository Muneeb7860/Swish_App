package ch.swissqcommerce.backend.domain.governance.core.service;

import java.util.Map;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutcomeResult {
    private Map<String, Object> metrics;
    private Boolean success;
    private String measurementWindow;
    private String notes;
}
