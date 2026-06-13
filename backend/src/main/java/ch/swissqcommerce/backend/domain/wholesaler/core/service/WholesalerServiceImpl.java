package ch.swissqcommerce.backend.domain.wholesaler.core.service;

import ch.swissqcommerce.backend.domain.sensor.port.out.SensorPort;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.domain.wholesaler.port.in.WholesalerUseCase;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.repository.DarkStoreRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WholesalerServiceImpl implements WholesalerUseCase {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(WholesalerServiceImpl.class);

    @Autowired private WholesalerPort wholesalerPort;

    @Autowired private B2BRestockOrderPort restockOrderPort;

    @Autowired
    private ch.swissqcommerce.backend.domain.wholesaler.port.out.PurchaseOrderPort
            purchaseOrderPort;

    @Autowired private LedgerUseCase ledgerService;

    @Autowired private SensorPort sensorPort;

    @Autowired private DarkStoreRepository darkStoreRepository;

    private boolean isStoreCompliant(String storeId) {
        if (sensorPort == null) return true;
        try {
            List<ch.swissqcommerce.backend.domain.sensor.core.model.Sensor> storeSensors =
                    sensorPort.findByStoreId(storeId);
            if (storeSensors == null) return true;
            for (var s : storeSensors) {
                if ("FAILED".equalsIgnoreCase(s.getCalibrationStatus())) {
                    return false;
                }
            }
        } catch (Exception e) {
            return true;
        }
        return true;
    }

    private String findAlternateCompliantStore(String currentStoreId) {
        if (darkStoreRepository == null) return currentStoreId;
        try {
            List<DarkStore> allStores = darkStoreRepository.findAll();
            if (allStores == null) return currentStoreId;
            for (DarkStore store : allStores) {
                if (store.getStoreId() != null
                        && !store.getStoreId().equals(currentStoreId)
                        && isStoreCompliant(store.getStoreId())) {
                    return store.getStoreId();
                }
            }
        } catch (Exception e) {
            return currentStoreId;
        }
        return currentStoreId;
    }

    @Override
    @Cacheable(value = "wholesaler-restocks", key = "#wholesalerId")
    public List<B2BRestockOrder> getAssignedRestocks(String wholesalerId) {
        wholesalerPort
                .findById(wholesalerId)
                .orElseThrow(
                        () -> new NoSuchElementException("Wholesaler not found: " + wholesalerId));
        return restockOrderPort.findByWholesalerId(wholesalerId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "wholesaler-restocks", allEntries = true)
    public B2BRestockOrder createRestockOrder(
            String storeId, String preferredWholesalerId, String idempotencyKey) {
        if (!isStoreCompliant(storeId)) {
            String alternateStoreId = findAlternateCompliantStore(storeId);
            log.warn(
                    "Store {} has failed sensors and is non-compliant. Rerouting B2B restock order"
                            + " to compliant store {}",
                    storeId,
                    alternateStoreId);
            storeId = alternateStoreId;
        }

        // Idempotency guard
        if (idempotencyKey != null) {
            Optional<B2BRestockOrder> existing =
                    restockOrderPort.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // Select primary wholesaler, fallback if primary is inactive or low trust
        Wholesaler selected;
        boolean isFallback = false;

        if (preferredWholesalerId != null) {
            selected =
                    wholesalerPort
                            .findById(preferredWholesalerId)
                            .orElseThrow(
                                    () ->
                                            new NoSuchElementException(
                                                    "Wholesaler not found: "
                                                            + preferredWholesalerId));
        } else {
            selected =
                    wholesalerPort
                            .findByIsPrimary(true)
                            .orElseThrow(
                                    () ->
                                            new NoSuchElementException(
                                                    "No primary wholesaler configured."));
        }

        final String currentSelectedId = selected.getWholesalerId();
        // Check if primary wholesaler is eligible (trust >= 60 and active).
        // Null trust/active flags (legacy rows) count as ineligible rather than NPE.
        if (!isEligible(selected)) {
            // Switch to fallback: highest-trust eligible wholesaler other than the
            // preferred one (max() keeps the choice deterministic, unlike findFirst()
            // over JPA's unspecified iteration order).
            selected =
                    wholesalerPort.findAll().stream()
                            .filter(
                                    w ->
                                            w.getWholesalerId() != null
                                                    && !w.getWholesalerId()
                                                            .equals(currentSelectedId)
                                                    && isEligible(w))
                            .max(Comparator.comparingInt(Wholesaler::getTrustScore))
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "No eligible wholesaler available for"
                                                            + " restock."));
            isFallback = true;
        }

        // Calculate invoice amount with academy discount
        BigDecimal invoiceAmount =
                isFallback ? selected.getFallbackInvoiceAmount() : selected.getBaseInvoiceAmount();
        if (invoiceAmount == null) {
            throw new IllegalStateException(
                    "Wholesaler "
                            + selected.getWholesalerId()
                            + " has no "
                            + (isFallback ? "fallback" : "base")
                            + " invoice amount configured.");
        }
        if (Boolean.TRUE.equals(selected.getAcademyDiscountActive())) {
            invoiceAmount = invoiceAmount.multiply(new BigDecimal("0.90")); // 10% discount
        }

        B2BRestockOrder restockOrder =
                B2BRestockOrder.builder()
                        .store(DarkStore.builder().storeId(storeId).build()) // Reference only
                        .wholesaler(selected)
                        .invoiceAmount(invoiceAmount)
                        .isFallback(isFallback)
                        .idempotencyKey(idempotencyKey)
                        .build();

        return restockOrderPort.save(restockOrder);
    }

    private static boolean isEligible(Wholesaler w) {
        return Boolean.TRUE.equals(w.getIsActive())
                && w.getTrustScore() != null
                && w.getTrustScore() >= 60;
    }

    @Override
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(value = "wholesaler-restocks", allEntries = true),
                @CacheEvict(value = "wholesaler-invoices", allEntries = true)
            })
    public Map<String, Object> fulfillRestock(Integer restockOrderId) {
        B2BRestockOrder restock =
                restockOrderPort
                        .findById(restockOrderId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Restock order not found: " + restockOrderId));

        if (!"pending".equalsIgnoreCase(restock.getStatus())) {
            throw new IllegalStateException(
                    "Restock order is not pending. Current: " + restock.getStatus());
        }

        restock.setStatus("fulfilled");
        restockOrderPort.save(restock);

        // Record B2B payment in double-entry ledger
        List<LedgerUseCase.LedgerLeg> legs =
                List.of(
                        new LedgerUseCase.LedgerLeg(
                                "system", null, restock.getInvoiceAmount(), BigDecimal.ZERO),
                        new LedgerUseCase.LedgerLeg(
                                "wholesaler",
                                restock.getWholesaler().getWholesalerId(),
                                BigDecimal.ZERO,
                                restock.getInvoiceAmount()));
        ledgerService.recordTransaction(
                "B2B-RESTOCK-PAY",
                "Restock fulfillment payment to wholesaler " + restock.getWholesaler().getName(),
                legs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "fulfilled");
        result.put("restockOrderId", restockOrderId);
        result.put("wholesaler", restock.getWholesaler().getName());
        result.put("invoiceAmount", restock.getInvoiceAmount());
        result.put("isFallback", restock.getIsFallback());
        return result;
    }

    @Override
    @Cacheable(value = "wholesaler-invoices", key = "#wholesalerId")
    public Map<String, Object> getInvoiceSummary(String wholesalerId) {
        Wholesaler wholesaler =
                wholesalerPort
                        .findById(wholesalerId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Wholesaler not found: " + wholesalerId));

        List<B2BRestockOrder> orders = restockOrderPort.findByWholesalerId(wholesalerId);

        BigDecimal totalFulfilled = BigDecimal.ZERO;
        long pendingCount = 0;
        for (B2BRestockOrder o : orders) {
            if ("fulfilled".equalsIgnoreCase(o.getStatus()) && o.getInvoiceAmount() != null) {
                totalFulfilled = totalFulfilled.add(o.getInvoiceAmount());
            } else if ("pending".equalsIgnoreCase(o.getStatus())) {
                pendingCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wholesalerId", wholesalerId);
        result.put("wholesalerName", wholesaler.getName());
        result.put("totalFulfilledInvoiceAmount", totalFulfilled);
        result.put("pendingOrderCount", pendingCount);
        result.put("totalOrderCount", orders.size());
        result.put("academyDiscountActive", wholesaler.getAcademyDiscountActive());
        result.put("trustScore", wholesaler.getTrustScore());
        return result;
    }

    @Override
    @Transactional
    public ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder
            generateReplenishmentOrders(
                    String storeId, String vendorName, Map<String, Integer> requestedItems) {
        ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder po =
                ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder.builder()
                        .poId(UUID.randomUUID().toString())
                        .storeId(storeId)
                        .vendorName(vendorName)
                        .status("DRAFT")
                        .createdAt(java.time.OffsetDateTime.now())
                        .build();

        List<ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrderItem> items =
                requestedItems.entrySet().stream()
                        .map(
                                e ->
                                        ch.swissqcommerce.backend.domain.wholesaler.core.model
                                                .PurchaseOrderItem.builder()
                                                .itemId(UUID.randomUUID().toString())
                                                .productId(e.getKey())
                                                .requestedQty(e.getValue())
                                                .receivedQty(0)
                                                .purchaseOrder(po)
                                                .build())
                        .toList();
        po.setItems(items);
        return purchaseOrderPort.savePurchaseOrder(po);
    }

    @Override
    @Transactional
    public ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder receiveGoods(
            String poId, Map<String, Integer> itemReceipts, String grnFileUrl) {
        ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder po =
                purchaseOrderPort
                        .findPurchaseOrder(poId)
                        .orElseThrow(() -> new IllegalArgumentException("PO not found: " + poId));

        boolean allFullyReceived = true;
        for (ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrderItem item :
                po.getItems()) {
            Integer recv = itemReceipts.get(item.getProductId());
            if (recv != null) {
                if (recv < 0) {
                    throw new IllegalArgumentException(
                            "Received quantity cannot be negative for product: "
                                    + item.getProductId());
                }
                if (item.getReceivedQty() + recv > item.getRequestedQty()) {
                    throw new IllegalArgumentException(
                            "Received quantity cannot exceed requested quantity for product: "
                                    + item.getProductId());
                }
                item.setReceivedQty(item.getReceivedQty() + recv);
            }
            if (item.getReceivedQty() < item.getRequestedQty()) {
                allFullyReceived = false;
            }
        }

        po.setStatus(allFullyReceived ? "RECEIVED" : "PARTIALLY_RECEIVED");
        po.setGrnVerificationFileUrl(grnFileUrl);
        po.setInboundDate(java.time.OffsetDateTime.now());
        po.setUpdatedAt(java.time.OffsetDateTime.now());
        return purchaseOrderPort.savePurchaseOrder(po);
    }

    @Override
    @Transactional
    public ch.swissqcommerce.backend.domain.wholesaler.core.model.WastageLog logWastage(
            String storeId,
            String productId,
            String batchId,
            Integer qty,
            String reason,
            String loggedBy) {
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("Wastage quantity must be greater than zero.");
        }

        ch.swissqcommerce.backend.domain.wholesaler.core.model.WastageLog log =
                ch.swissqcommerce.backend.domain.wholesaler.core.model.WastageLog.builder()
                        .logId(java.util.UUID.randomUUID().toString())
                        .storeId(storeId)
                        .productId(productId)
                        .batchId(batchId)
                        .qtyWasted(qty)
                        .reason(reason)
                        .loggedBy(loggedBy)
                        .timestamp(java.time.OffsetDateTime.now())
                        .build();
        return purchaseOrderPort.saveWastageLog(log);
    }
}
