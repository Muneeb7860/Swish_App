package ch.swissqcommerce.backend.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
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

    /**
     * Uses the Cloud Orchestrator (e.g., Gemini) for heavy reasoning tasks.
     * Streams the response back token-by-token.
     */
    public Flux<String> orchestrateComplexTask(String promptText) {
        return cloudChatModel.stream(promptText);
    }

    /**
     * Uses the local Ollama Model (e.g., Qwen) for local telemetry or db-related queries.
     * Streams the response back token-by-token.
     */
    public Flux<String> executeLocalTask(String promptText) {
        return localChatModel.stream(promptText);
    }
}
