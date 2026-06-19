package ch.swissqcommerce.backend.domain.logistics.adapter.out.carrier;

import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.CarrierRate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class CarrierRateAdapter {

    private static final Logger log = LoggerFactory.getLogger(CarrierRateAdapter.class);

    private final RestTemplate restTemplate;

    @Value("${carrier.api.url:http://localhost:8083/api/carrier/rate}")
    private String carrierApiUrl;

    public CarrierRateAdapter(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(200);
                    factory.setReadTimeout(200);
                    return factory;
                })
                .build();
    }

    public Optional<CarrierRate> getCarrierRate(String warehouseId, String destinationZip) {
        try {
            String url = carrierApiUrl + "?warehouseId=" + warehouseId + "&destinationZip=" + destinationZip;
            log.debug("Calling carrier rate API: {}", url);

            Map<?, ?> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("rate")) {
                Object rateObj = response.get("rate");
                String carrier = response.containsKey("carrier") ? (String) response.get("carrier") : "UPS";
                if (rateObj instanceof Number num) {
                    return Optional.of(new CarrierRate(carrier, BigDecimal.valueOf(num.doubleValue())));
                }
            }
        } catch (ResourceAccessException e) {
            log.warn("Carrier rate API read/connect timeout (200ms) or connection refused for warehouse={}, zip={}. Error: {}", 
                     warehouseId, destinationZip, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Carrier rate API invocation failed for warehouse={}, zip={}. Error: {}", 
                     warehouseId, destinationZip, e.getMessage());
        }
        return Optional.empty();
    }
}
