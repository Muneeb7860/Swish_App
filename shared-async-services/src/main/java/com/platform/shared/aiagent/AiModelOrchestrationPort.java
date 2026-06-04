package com.platform.shared.aiagent;

/**
 * Hexagonal Outbound Port for abstracting AI Model execution and cancellation.
 * Implementations will vary based on deployment profile (Local Ollama vs Cloud Vertex AI).
 */
public interface AiModelOrchestrationPort {

    /**
     * Executes the reactive LangGraph state machine node.
     */
    String executeModelPrompt(String promptPayload);

    /**
     * Active Abort Interceptor.
     * Cancels an active inference session on the GPU/Cloud instance if a timeout is reached.
     */
    void cancelInferenceSession(String sessionId);
}
