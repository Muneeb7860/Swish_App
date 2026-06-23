package com.platform.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/fallback")
public class FallbackController {

    @GetMapping("/rewards")
    public Mono<ResponseEntity<Map<String, String>>> rewardsFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "UPSTREAM_ERROR",
                        "message", "The Rewards service is temporarily unavailable. Please try again later."
                )));
    }

    @GetMapping("/ledger")
    public Mono<ResponseEntity<Map<String, String>>> ledgerFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "UPSTREAM_ERROR",
                        "message", "The Ledger service is temporarily unavailable. Please try again later."
                )));
    }
}
