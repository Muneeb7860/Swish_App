package ch.swissqcommerce.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class LettaConfig {

    @Value("${swish.letta.api.url:http://localhost:8283}")
    private String apiUrl;

    @Value("${swish.letta.api.token:dummy-key}")
    private String apiToken;

    // Default to a LOCAL Ollama model (free/local-only AI policy). The Letta
    // container ships a dummy OPENAI_API_KEY, so an openai/* default could never
    // authenticate anyway — and a paid model must never be the default.
    // Override via SWISH_LETTA_MODEL.
    @Value("${swish.letta.model:ollama/qwen2.5:7b}")
    private String model;

    @Bean
    public RestTemplate lettaRestTemplate(RestTemplateBuilder builder) {
        return builder.setConnectTimeout(java.time.Duration.ofMillis(5000))
                .setReadTimeout(java.time.Duration.ofMillis(10000))
                .build();
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getModel() {
        return model;
    }
}
