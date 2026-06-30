package ch.swissqcommerce.backend.domain.logistics.adapter.out.carrier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.CarrierRate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
public class CarrierRateAdapterTest {

    private CarrierRateAdapter adapter;

    @Mock private CarrierRateClient carrierRateClient;

    @BeforeEach
    void setUp() {
        adapter = new CarrierRateAdapter(carrierRateClient);
    }

    @Test
    void testGetCarrierRate_Success() {
        Map mockResponse = Map.of("carrier", "UPS", "rate", 8.50);
        when(carrierRateClient.callCarrierRateApi(anyString())).thenReturn(mockResponse);

        Optional<CarrierRate> rateOpt = adapter.getCarrierRate("WH-NY-01", "80012");
        assertTrue(rateOpt.isPresent());
        assertEquals("UPS", rateOpt.get().carrier());
        assertEquals(BigDecimal.valueOf(8.5), rateOpt.get().rate());
    }

    @Test
    void testGetCarrierRate_TimeoutException() {
        when(carrierRateClient.callCarrierRateApi(anyString()))
                .thenThrow(new ResourceAccessException("Timeout"));

        Optional<CarrierRate> rateOpt = adapter.getCarrierRate("WH-NY-01", "80012");
        assertFalse(rateOpt.isPresent());
    }

    @Test
    void testGetCarrierRate_CircuitOpenException() {
        when(carrierRateClient.callCarrierRateApi(anyString()))
                .thenThrow(
                        io.github.resilience4j.circuitbreaker.CallNotPermittedException
                                .createCallNotPermittedException(
                                        io.github.resilience4j.circuitbreaker.CircuitBreaker
                                                .ofDefaults("carrierRate")));

        Optional<CarrierRate> rateOpt = adapter.getCarrierRate("WH-NY-01", "80012");
        assertFalse(rateOpt.isPresent());
    }

    @Test
    void testGetCarrierRate_GenericException() {
        when(carrierRateClient.callCarrierRateApi(anyString()))
                .thenThrow(new RuntimeException("API error"));

        Optional<CarrierRate> rateOpt = adapter.getCarrierRate("WH-NY-01", "80012");
        assertFalse(rateOpt.isPresent());
    }
}
