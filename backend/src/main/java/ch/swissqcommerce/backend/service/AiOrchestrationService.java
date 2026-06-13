package ch.swissqcommerce.backend.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AiOrchestrationService {

    private final ChatModel cloudChatModel;
    private final ChatModel localChatModel;

    @Autowired
    public AiOrchestrationService(OpenAiChatModel cloudChatModel, OllamaChatModel localChatModel) {
        this.cloudChatModel = cloudChatModel;
        this.localChatModel = localChatModel;
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AiOrchestrationService.class);

    /**
     * Uses the Cloud Orchestrator (e.g., Gemini) for heavy reasoning tasks. Streams the response
     * back token-by-token.
     */
    public Flux<String> orchestrateComplexTask(String promptText) {
        try {
            if (cloudChatModel == null) {
                return Flux.just("Error: Cloud AI reasoning model is not configured.");
            }
            return cloudChatModel.stream(promptText)
                    .onErrorResume(
                            ex -> {
                                log.error(
                                        "AiOrchestrationService: Cloud AI stream error: {}",
                                        ex.getMessage());
                                return Flux.just(
                                        "Error: Cloud AI reasoning service connection failed.");
                            });
        } catch (Exception e) {
            log.error("AiOrchestrationService: Failed to initiate cloud task: {}", e.getMessage());
            return Flux.just("Error: Cloud AI reasoning service initialization failed.");
        }
    }

    /**
     * Uses the local Ollama Model (e.g., Qwen) for local telemetry or db-related queries. Streams
     * the response back token-by-token.
     */
    public Flux<String> executeLocalTask(String promptText) {
        try {
            if (localChatModel == null) {
                return Flux.just("Error: Local AI reasoning model is not configured.");
            }
            return localChatModel.stream(promptText)
                    .onErrorResume(
                            ex -> {
                                log.error(
                                        "AiOrchestrationService: Local AI stream error: {}",
                                        ex.getMessage());
                                return Flux.just(
                                        "Error: Local AI reasoning service connection failed.");
                            });
        } catch (Exception e) {
            log.error("AiOrchestrationService: Failed to initiate local task: {}", e.getMessage());
            return Flux.just("Error: Local AI reasoning service initialization failed.");
        }
    }
}
