package ch.swissqcommerce.backend.controller;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CarrierMockController {

    private static final Logger log = LoggerFactory.getLogger(CarrierMockController.class);

    @GetMapping("/api/carrier/rate")
    public Map<String, Object> getRate(
            @RequestParam String warehouseId,
            @RequestParam String destinationZip) {
        log.info("CarrierMockController: rate requested for warehouse={} and zip={}", warehouseId, destinationZip);

        double zipMultiplier = 1.0;
        try {
            if (destinationZip != null && destinationZip.length() >= 3) {
                zipMultiplier = Double.parseDouble(destinationZip.substring(0, 3)) / 100.0;
            }
        } catch (NumberFormatException e) {
            // ignore
        }

        double rate = 5.0 + (zipMultiplier * 0.5);
        return Map.of(
                "carrier", "UPS",
                "rate", rate
        );
    }
}
