package ch.swissqcommerce.backend.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.catalog.port.out.FmcgApiPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
public class FmcgResilienceIntegrationTest {

    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired private FmcgApiPort openFoodFactsClient;

    @Test
    public void testFmcgApiCircuitBreaker_TripsOnFailure() throws Exception {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("fmcg-api");
        assertNotNull(circuitBreaker, "fmcg-api circuit breaker must be configured");

        // Force reset the state to CLOSED for clean test execution
        circuitBreaker.reset();
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        // Mock the internal HttpClient to throw IOException (Simulating connection failures)
        HttpClient mockHttpClient = mock(HttpClient.class);
        when(mockHttpClient.send(any(), any())).thenThrow(new IOException("Connection timed out"));

        // Inject the mock HttpClient using Spring's ReflectionTestUtils
        Object originalHttpClient = ReflectionTestUtils.getField(openFoodFactsClient, "httpClient");
        ReflectionTestUtils.setField(openFoodFactsClient, "httpClient", mockHttpClient);

        try {
            // Call the client. Sliding window size is 5, failure rate is 50%.
            // Making 5 failed calls will trip the circuit breaker.
            for (int i = 0; i < 5; i++) {
                try {
                    openFoodFactsClient.fetchProduct("7613035449626");
                } catch (Exception ignored) {
                    // exception captured to allow continuation of loops
                }
            }

            // Assert that the circuit breaker state has transitioned to OPEN
            assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

            // Under OPEN state, subsequent calls should immediately short-circuit
            // and throw CallNotPermittedException directly from Resilience4j
            assertThrows(
                    CallNotPermittedException.class,
                    () -> openFoodFactsClient.fetchProduct("7613035449626"));

        } finally {
            // Restore the original HttpClient to maintain clean context state
            ReflectionTestUtils.setField(openFoodFactsClient, "httpClient", originalHttpClient);
            circuitBreaker.reset();
        }
    }
}
