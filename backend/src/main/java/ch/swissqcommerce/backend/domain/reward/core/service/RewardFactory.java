package ch.swissqcommerce.backend.domain.reward.core.service;

import ch.swissqcommerce.backend.domain.reward.core.model.RewardType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RewardFactory {

    private final Map<RewardType, RewardProcessor> processors;

    public RewardFactory(List<RewardProcessor> processorList) {
        this.processors = processorList.stream()
                .collect(Collectors.toMap(RewardProcessor::getType, p -> p));
    }

    public RewardProcessor getProcessor(RewardType type) {
        RewardProcessor processor = processors.get(type);
        if (processor == null) {
            throw new IllegalArgumentException("Unsupported reward type: " + type);
        }
        return processor;
    }
}
