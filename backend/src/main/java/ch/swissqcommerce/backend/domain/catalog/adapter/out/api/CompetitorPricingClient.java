package ch.swissqcommerce.backend.domain.catalog.adapter.out.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CompetitorPricingClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @CircuitBreaker(name = "competitor-api")
    public Optional<Double> fetchCompetitorPrice(String barcode) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "http://localhost:8089/api/v1/competitor/price/"
                                                    + barcode))
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<?, ?> body = objectMapper.readValue(response.body(), Map.class);
                Object priceObj = body.get("competitorPrice");
                if (priceObj instanceof Number) {
                    return Optional.of(((Number) priceObj).doubleValue());
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Error calling Competitor Pricing API for barcode {}: {}",
                    barcode,
                    e.getMessage());
            throw new RuntimeException("Competitor API Call failed", e);
        }
        return Optional.empty();
    }
}
