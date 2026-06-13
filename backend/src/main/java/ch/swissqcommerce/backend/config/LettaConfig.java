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

    @Value("${swish.letta.model:openai/gpt-4o}")
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
