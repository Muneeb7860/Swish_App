package ch.swissqcommerce.backend.domain.logistics.adapter.out.carrier;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Resilience4j-guarded client for the carrier rate API to separate proxy interception from error
 * logging.
 */
@Component
public class CarrierRateClient {

    private final RestTemplate restTemplate;

    public CarrierRateClient(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate =
                restTemplateBuilder
                        .requestFactory(
                                () -> {
                                    SimpleClientHttpRequestFactory factory =
                                            new SimpleClientHttpRequestFactory();
                                    factory.setConnectTimeout(200);
                                    factory.setReadTimeout(200);
                                    return factory;
                                })
                        .build();
    }

    /**
     * Invokes the carrier rate API with a circuit breaker named "carrierRate".
     *
     * @param url the full request URL
     * @return the raw API response map
     */
    @CircuitBreaker(name = "carrierRate")
    public Map<?, ?> callCarrierRateApi(String url) {
        return restTemplate.getForObject(url, Map.class);
    }
}
