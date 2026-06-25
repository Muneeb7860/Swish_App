package ch.swissqcommerce.backend.domain.logistics.adapter.out.carrier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort.CarrierRate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

public class CarrierRateAdapterTest {

    private CarrierRateAdapter adapter;

    @Mock private RestTemplate restTemplate;

    @Mock private RestTemplateBuilder restTemplateBuilder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(restTemplateBuilder.requestFactory(any(java.util.function.Supplier.class)))
                .thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        adapter = new CarrierRateAdapter(restTemplateBuilder);
    }

    @Test
    void testGetCarrierRate_Success() {
        Map<String, Object> mockResponse = Map.of("carrier", "UPS", "rate", 8.50);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockResponse);

        Optional<CarrierRate> rateOpt = adapter.getCarrierRate("WH-NY-01", "80012");
        assertTrue(rateOpt.isPresent());
        assertEquals("UPS", rateOpt.get().carrier());
        assertEquals(BigDecimal.valueOf(8.5), rateOpt.get().rate());
    }

    @Test
    void testGetCarrierRate_TimeoutException() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Timeout"));

        Optional<CarrierRate> rateOpt = adapter.getCarrierRate("WH-NY-01", "80012");
        assertFalse(rateOpt.isPresent()); // Returns empty immediately on timeout
    }

    @Test
    void testGetCarrierRate_GenericException() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("API error"));

        Optional<CarrierRate> rateOpt = adapter.getCarrierRate("WH-NY-01", "80012");
        assertFalse(rateOpt.isPresent());
    }
}
