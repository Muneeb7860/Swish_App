package ch.swissqcommerce.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.HashMap;

/**
 * Enterprise BFF Gateway application.
 * Exposes a fallback API endpoint mapped to the gateway circuit breaker.
 */
@SpringBootApplication
@RestController
public class BffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }

    @GetMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("code", 503);
        response.put("message", "The downstream checkout service is currently experiencing high latency or is offline. Route isolation circuit breaker tripped.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
