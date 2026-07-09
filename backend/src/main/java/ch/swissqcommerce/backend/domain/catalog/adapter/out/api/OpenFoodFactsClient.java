package ch.swissqcommerce.backend.domain.catalog.adapter.out.api;

import ch.swissqcommerce.backend.domain.catalog.port.out.FmcgApiPort;
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
public class OpenFoodFactsClient implements FmcgApiPort {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @Override
    @CircuitBreaker(name = "fmcgApi")
    public Optional<FmcgProductDto> fetchProduct(String barcode) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "https://world.openfoodfacts.org/api/v2/product/"
                                                    + barcode
                                                    + ".json?fields=code,product_name,brands,categories,image_front_url"))
                            .header(
                                    "User-Agent",
                                    "SwishApp - Catalog Seeder - Version 1.0 - admin@swish.ch")
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<?, ?> body = objectMapper.readValue(response.body(), Map.class);
                if (Integer.valueOf(1).equals(body.get("status"))
                        || "product found".equals(body.get("status_verbose"))) {
                    Map<?, ?> product = (Map<?, ?>) body.get("product");
                    String apiName = (String) product.get("product_name");
                    String apiBrand = (String) product.get("brands");
                    String brand = null;
                    if (apiBrand != null && !apiBrand.isBlank()) {
                        brand = apiBrand.split(",")[0].trim();
                    }
                    if (apiName != null && !apiName.isBlank()) {
                        return Optional.of(new FmcgProductDto(apiName, brand));
                    }
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Error calling Open Food Facts API for barcode {}: {}",
                    barcode,
                    e.getMessage());
            throw new RuntimeException("API Call failed", e);
        }
        return Optional.empty();
    }
}
