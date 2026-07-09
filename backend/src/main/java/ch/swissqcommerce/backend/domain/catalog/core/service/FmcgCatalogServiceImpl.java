package ch.swissqcommerce.backend.domain.catalog.core.service;

import ch.swissqcommerce.backend.domain.agent.core.service.DynamicPricingAgent;
import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import ch.swissqcommerce.backend.domain.catalog.port.in.FmcgCatalogUseCase;
import ch.swissqcommerce.backend.domain.catalog.port.out.CatalogPort;
import ch.swissqcommerce.backend.domain.transaction.port.out.DarkStorePort;
import ch.swissqcommerce.backend.domain.transaction.port.out.InventoryPort;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.model.Inventory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FmcgCatalogServiceImpl implements FmcgCatalogUseCase {

    private final InventoryPort inventoryPort;
    private final DarkStorePort darkStorePort;
    private final CatalogPort catalogPort;
    private final DynamicPricingAgent dynamicPricingAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, FmcgFallback> FALLBACKS =
            Map.of(
                    "7613035449626",
                            new FmcgFallback(
                                    "NESTLE CHOCAPIC Céréales 645g",
                                    "Nestlé",
                                    "Snacks & Drinks",
                                    "🍫",
                                    new BigDecimal("4.50"),
                                    true),
                    "7622202225512",
                            new FmcgFallback(
                                    "Original Oreo Biscuits",
                                    "Cadbury",
                                    "Snacks & Drinks",
                                    "🍪",
                                    new BigDecimal("2.20"),
                                    false),
                    "3045140118502",
                            new FmcgFallback(
                                    "Milka Tablette Noisette",
                                    "Mondelez",
                                    "Snacks & Drinks",
                                    "🍫",
                                    new BigDecimal("3.00"),
                                    false),
                    "5449000000996",
                            new FmcgFallback(
                                    "Coca-Cola Original 330ml",
                                    "Coca-Cola",
                                    "Snacks & Drinks",
                                    "🥤",
                                    new BigDecimal("1.80"),
                                    false),
                    "3168930173373",
                            new FmcgFallback(
                                    "Doritos Nacho Cheese 150g",
                                    "PepsiCo",
                                    "Snacks & Drinks",
                                    "🍿",
                                    new BigDecimal("2.90"),
                                    false),
                    "8712100441660",
                            new FmcgFallback(
                                    "Maille Vinaigre Balsamique",
                                    "Unilever",
                                    "Grocery & Kitchen",
                                    "🏺",
                                    new BigDecimal("5.50"),
                                    false),
                    "8901207025365",
                            new FmcgFallback(
                                    "Dabur Pure Honey 250g",
                                    "Dabur",
                                    "Grocery & Kitchen",
                                    "🍯",
                                    new BigDecimal("6.50"),
                                    false),
                    "8901138834165",
                            new FmcgFallback(
                                    "Himalaya Neem Face Wash 150ml",
                                    "Himalaya",
                                    "Lifestyle",
                                    "🧴",
                                    new BigDecimal("7.90"),
                                    false),
                    "8901725198129",
                            new FmcgFallback(
                                    "Sunfeast Dark Fantasy Cookies",
                                    "ITC",
                                    "Snacks & Drinks",
                                    "🍪",
                                    new BigDecimal("3.50"),
                                    false),
                    "8001090224577",
                            new FmcgFallback(
                                    "Pampers Baby Dry Diapers",
                                    "P&G",
                                    "Lifestyle",
                                    "👶",
                                    new BigDecimal("12.90"),
                                    false));

    @Data
    @AllArgsConstructor
    private static class FmcgFallback {
        private String name;
        private String brand;
        private String category;
        private String emoji;
        private BigDecimal basePrice;
        private boolean perishable;
    }

    @Override
    @Transactional
    public List<FmcgImportResult> importFmcgProducts() {
        HttpClient client =
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

        List<FmcgImportResult> results = new ArrayList<>();

        // Fetch target dark stores
        DarkStore store1 = darkStorePort.findDarkStoreById("store-1").orElse(null);
        DarkStore store2 = darkStorePort.findDarkStoreById("store-2").orElse(null);

        for (Map.Entry<String, FmcgFallback> entry : FALLBACKS.entrySet()) {
            String barcode = entry.getKey();
            FmcgFallback fallback = entry.getValue();

            String name = fallback.getName();
            String brand = fallback.getBrand();
            String category = fallback.getCategory();
            String emoji = fallback.getEmoji();
            BigDecimal basePrice = fallback.getBasePrice();
            boolean perishable = fallback.isPerishable();
            String source = "FALLBACK";

            // Try Live API
            try {
                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(
                                        URI.create(
                                                "https://world.openfoodfacts.org/api/v2/product/"
                                                        + barcode
                                                        + ".json?fields=code,product_name,brands,categories,image_front_url"))
                                .header(
                                        "User-Agent",
                                        "SwishApp - Catalog Seeder - Version 1.0 - admin@swish.ch")
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    Map<?, ?> body = objectMapper.readValue(response.body(), Map.class);
                    if (Integer.valueOf(1).equals(body.get("status"))
                            || "product found".equals(body.get("status_verbose"))) {
                        Map<?, ?> product = (Map<?, ?>) body.get("product");
                        String apiName = (String) product.get("product_name");
                        String apiBrand = (String) product.get("brands");
                        if (apiName != null && !apiName.isBlank()) {
                            name = apiName;
                            source = "API";
                        }
                        if (apiBrand != null && !apiBrand.isBlank()) {
                            // Extract primary brand if multiple comma-separated exist
                            brand = apiBrand.split(",")[0].trim();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn(
                        "Failed to fetch product {} from Open Food Facts API (using fallback): {}",
                        barcode,
                        e.getMessage());
            }

            // Calculate Dynamic Price
            // We simulate: false (isRaining), 1.2 (riderToOrderRatio),
            // competitor price is 5% cheaper, 45 (daysToExpiry) (or 2 if perishable to trigger
            // discounts), 0.20 (vipDensity)
            double competitorPriceVal = basePrice.multiply(new BigDecimal("0.95")).doubleValue();
            double surge = 1.0;
            double discount = 0.0;
            String rationale = "In-house fallback applied.";

            try {
                DynamicPricingAgent.PricingAnalysis analysis =
                        dynamicPricingAgent.recommendPricing(
                                false, 1.2, competitorPriceVal, perishable ? 2 : 45, 0.20);
                if (analysis != null) {
                    surge = analysis.surgeMultiplier;
                    discount = analysis.discountPercent;
                    rationale = analysis.rationale;
                }
            } catch (Exception e) {
                log.warn(
                        "Dynamic pricing recommendPricing failed for product {} (using standard"
                                + " fallback): {}",
                        barcode,
                        e.getMessage());
            }

            BigDecimal finalPrice =
                    basePrice
                            .multiply(BigDecimal.valueOf(surge))
                            .multiply(BigDecimal.valueOf(1 - discount / 100.0))
                            .setScale(2, RoundingMode.HALF_UP);

            String itemId = "brand-" + barcode;

            // Save to store-1 if present
            if (store1 != null) {
                Inventory inv =
                        inventoryPort
                                .findInventoryById(itemId)
                                .orElse(
                                        Inventory.builder()
                                                .itemId(itemId)
                                                .store(store1)
                                                .name(name)
                                                .category(category)
                                                .emoji(emoji)
                                                .perishable(perishable)
                                                .version(0L)
                                                .build());
                inv.setPrice(finalPrice);
                // Give a realistic stock value
                inv.setStock(50 + (int) (Math.random() * 20));
                inventoryPort.save(inv);
            }

            // Save to store-2 if present
            if (store2 != null) {
                Inventory inv2 =
                        inventoryPort
                                .findInventoryById(itemId + "-geneva")
                                .orElse(
                                        Inventory.builder()
                                                .itemId(itemId + "-geneva")
                                                .store(store2)
                                                .name(name)
                                                .category(category)
                                                .emoji(emoji)
                                                .perishable(perishable)
                                                .version(0L)
                                                .build());
                inv2.setPrice(finalPrice);
                inv2.setStock(30 + (int) (Math.random() * 20));
                inventoryPort.save(inv2);
            }

            // Save ProductListing
            ProductListing listing =
                    catalogPort
                            .findById(itemId)
                            .orElse(
                                    ProductListing.builder()
                                            .productId(itemId)
                                            .title(name)
                                            .description(
                                                    "Imported real FMCG product from "
                                                            + brand
                                                            + ".")
                                            .gallery(Collections.emptyList())
                                            .status("ACTIVE")
                                            .build());
            listing.updatePrice(finalPrice);
            catalogPort.save(listing);

            results.add(
                    FmcgImportResult.builder()
                            .barcode(barcode)
                            .name(name)
                            .brand(brand)
                            .category(category)
                            .emoji(emoji)
                            .basePrice(basePrice)
                            .dynamicPrice(finalPrice)
                            .surgeMultiplier(surge)
                            .discountPercent(discount)
                            .pricingRationale(rationale)
                            .source(source)
                            .status("SUCCESS")
                            .build());
        }

        return results;
    }
}
