package ch.swissqcommerce.backend.domain.governance.adapter.in.scheduler;

import ch.swissqcommerce.backend.domain.governance.port.in.OutcomeEvaluationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutcomeJob {

    private static final Logger log = LoggerFactory.getLogger(OutcomeJob.class);

    private final OutcomeEvaluationUseCase outcomeEvaluationUseCase;

    public OutcomeJob(OutcomeEvaluationUseCase outcomeEvaluationUseCase) {
        this.outcomeEvaluationUseCase = outcomeEvaluationUseCase;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runOutcomeEvaluationScheduled() {
        log.info("OutcomeJob: Triggered scheduled outcome evaluation.");
        outcomeEvaluationUseCase.runOutcomeEvaluation();
    }

    public void runOutcomeEvaluation() {
        outcomeEvaluationUseCase.runOutcomeEvaluation();
    }
}
