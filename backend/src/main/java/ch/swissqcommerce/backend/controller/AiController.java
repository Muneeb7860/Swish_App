package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.service.AiOrchestrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiOrchestrationService aiOrchestrationService;

    @Autowired
    public AiController(AiOrchestrationService aiOrchestrationService) {
        this.aiOrchestrationService = aiOrchestrationService;
    }

    @PostMapping(value = "/orchestrate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> orchestrate(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return Flux.just("Error: Prompt is required");
        }
        return aiOrchestrationService.orchestrateComplexTask(prompt);
    }

    @PostMapping(value = "/local", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> localTask(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return Flux.just("Error: Prompt is required");
        }
        return aiOrchestrationService.executeLocalTask(prompt);
    }
}
