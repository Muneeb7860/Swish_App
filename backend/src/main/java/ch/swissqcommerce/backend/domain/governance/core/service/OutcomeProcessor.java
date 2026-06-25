package ch.swissqcommerce.backend.domain.governance.core.service;

import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import ch.swissqcommerce.backend.model.ExecutionRecord;

public interface OutcomeProcessor {
    OutcomeResult evaluate(ExecutionRecord exec, AgentSuggestionEntity suggestion) throws Exception;

    String domain();
}
