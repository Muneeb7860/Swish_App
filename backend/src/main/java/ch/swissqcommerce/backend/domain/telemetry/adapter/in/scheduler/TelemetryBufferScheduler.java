package ch.swissqcommerce.backend.domain.telemetry.adapter.in.scheduler;

import ch.swissqcommerce.backend.domain.telemetry.port.in.TelemetryUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TelemetryBufferScheduler {

    private final TelemetryUseCase telemetryUseCase;

    @Autowired
    public TelemetryBufferScheduler(TelemetryUseCase telemetryUseCase) {
        this.telemetryUseCase = telemetryUseCase;
    }

    @Scheduled(fixedDelay = 10000)
    public void flushTickBuffer() {
        telemetryUseCase.flushTickBuffer();
    }
}
