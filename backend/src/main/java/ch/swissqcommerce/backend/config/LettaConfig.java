package ch.swissqcommerce.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    public RestTemplate lettaRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 seconds
        factory.setReadTimeout(10000);    // 10 seconds
        return new RestTemplate(factory);
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

