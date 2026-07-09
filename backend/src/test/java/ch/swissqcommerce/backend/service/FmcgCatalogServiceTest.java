package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.agent.core.service.DynamicPricingAgent;
import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import ch.swissqcommerce.backend.domain.catalog.core.service.FmcgCatalogServiceImpl;
import ch.swissqcommerce.backend.domain.catalog.port.in.FmcgCatalogUseCase.FmcgImportResult;
import ch.swissqcommerce.backend.domain.catalog.port.out.CatalogPort;
import ch.swissqcommerce.backend.domain.catalog.port.out.FmcgApiPort;
import ch.swissqcommerce.backend.domain.transaction.port.out.DarkStorePort;
import ch.swissqcommerce.backend.domain.transaction.port.out.InventoryPort;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.model.Inventory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FmcgCatalogServiceTest {

    @Mock private InventoryPort inventoryPort;
    @Mock private DarkStorePort darkStorePort;
    @Mock private CatalogPort catalogPort;
    @Mock private DynamicPricingAgent dynamicPricingAgent;
    @Mock private FmcgApiPort fmcgApiPort;

    private FmcgCatalogServiceImpl fmcgCatalogService;

    @BeforeEach
    public void setUp() {
        fmcgCatalogService =
                new FmcgCatalogServiceImpl(
                        inventoryPort,
                        darkStorePort,
                        catalogPort,
                        dynamicPricingAgent,
                        fmcgApiPort);
    }

    @Test
    public void testImportFmcgProducts_Success() {
        // Mock FmcgApiPort
        when(fmcgApiPort.fetchProduct(anyString()))
                .thenAnswer(
                        invocation -> {
                            String barcode = invocation.getArgument(0);
                            return Optional.of(
                                    new FmcgApiPort.FmcgProductDto(
                                            "Mocked Product Name for " + barcode, "Mocked Brand"));
                        });

        // Mock DarkStore lookups
        DarkStore store1 = DarkStore.builder().storeId("store-1").storeName("Zurich").build();
        DarkStore store2 = DarkStore.builder().storeId("store-2").storeName("Geneva").build();
        when(darkStorePort.findDarkStoreById("store-1")).thenReturn(Optional.of(store1));
        when(darkStorePort.findDarkStoreById("store-2")).thenReturn(Optional.of(store2));

        // Mock DynamicPricingAgent responses
        DynamicPricingAgent.PricingAnalysis pricingAnalysis =
                new DynamicPricingAgent.PricingAnalysis(
                        1.2, // surge
                        10.0, // discount
                        0.95, // confidence
                        "Favorable zone supply dynamics", // rationale
                        0.001, // cost
                        false // fallback applied
                        );
        when(dynamicPricingAgent.recommendPricing(
                        anyBoolean(), anyDouble(), anyDouble(), anyInt(), anyDouble()))
                .thenReturn(pricingAnalysis);

        // Mock inventoryPort check (empty/not found so it builds a new one)
        when(inventoryPort.findInventoryById(anyString())).thenReturn(Optional.empty());

        // Mock catalogPort check (empty/not found so it builds a new listing)
        when(catalogPort.findById(anyString())).thenReturn(Optional.empty());

        // Execute import
        List<FmcgImportResult> results = fmcgCatalogService.importFmcgProducts();

        // Verify size
        assertNotNull(results);
        assertEquals(10, results.size());

        // Verify content
        for (FmcgImportResult res : results) {
            assertEquals("SUCCESS", res.getStatus());
            assertNotNull(res.getBarcode());
            assertNotNull(res.getName());
            assertNotNull(res.getBrand());
            assertNotNull(res.getCategory());
            assertNotNull(res.getEmoji());
            assertNotNull(res.getBasePrice());
            assertNotNull(res.getDynamicPrice());
            assertEquals(1.2, res.getSurgeMultiplier());
            assertEquals(10.0, res.getDiscountPercent());
            assertNotNull(res.getPricingRationale());
            // Verify that the final price is correct: basePrice * 1.2 * 0.9 = basePrice * 1.08
            BigDecimal expectedPrice =
                    res.getBasePrice()
                            .multiply(BigDecimal.valueOf(1.2))
                            .multiply(BigDecimal.valueOf(0.9))
                            .setScale(2, java.math.RoundingMode.HALF_UP);
            assertEquals(expectedPrice, res.getDynamicPrice());
        }

        // Verify repository interactions
        verify(inventoryPort, atLeast(10)).save(any(Inventory.class));
        verify(catalogPort, atLeast(10)).save(any(ProductListing.class));
    }
}
