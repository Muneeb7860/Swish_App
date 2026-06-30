package ch.swissqcommerce.backend.domain.logistics.adapter.out.carrier;

import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.CarrierRate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/**
 * Adapter that invokes the carrier rate API to retrieve shipping quotes. Delegates the network call
 * to {@link CarrierRateClient} which is decorated with a Resilience4j circuit breaker.
 */
@Component
public class CarrierRateAdapter {

    private static final Logger log = LoggerFactory.getLogger(CarrierRateAdapter.class);

    private final CarrierRateClient carrierRateClient;

    @Value("${carrier.api.url:http://localhost:8083/api/carrier/rate}")
    private String carrierApiUrl;

    public CarrierRateAdapter(CarrierRateClient carrierRateClient) {
        this.carrierRateClient = carrierRateClient;
    }

    public Optional<CarrierRate> getCarrierRate(String warehouseId, String destinationZip) {
        try {
            String url =
                    carrierApiUrl
                            + "?warehouseId="
                            + warehouseId
                            + "&destinationZip="
                            + destinationZip;
            log.debug("Calling carrier rate API: {}", url);

            Map<?, ?> response = carrierRateClient.callCarrierRateApi(url);
            if (response != null && response.containsKey("rate")) {
                Object rateObj = response.get("rate");
                String carrier =
                        response.containsKey("carrier") ? (String) response.get("carrier") : "UPS";
                if (rateObj instanceof Number num) {
                    return Optional.of(
                            new CarrierRate(carrier, BigDecimal.valueOf(num.doubleValue())));
                }
            }
        } catch (ResourceAccessException e) {
            log.warn(
                    "Carrier rate API read/connect timeout (200ms) or connection refused for"
                            + " warehouse={}, zip={}. Error: {}",
                    warehouseId,
                    destinationZip,
                    e.getMessage());
            return Optional.empty();
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            log.warn(
                    "Carrier rate API call blocked by open circuit breaker for warehouse={},"
                            + " zip={}",
                    warehouseId,
                    destinationZip);
            return Optional.empty();
        } catch (Exception e) {
            log.warn(
                    "Carrier rate API invocation failed for warehouse={}, zip={}. Error: {}",
                    warehouseId,
                    destinationZip,
                    e.getMessage());
        }
        return Optional.empty();
    }
}
