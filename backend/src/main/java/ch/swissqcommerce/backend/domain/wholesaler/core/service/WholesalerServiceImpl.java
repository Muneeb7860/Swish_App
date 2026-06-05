package ch.swissqcommerce.backend.domain.wholesaler.core.service;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import ch.swissqcommerce.backend.domain.wholesaler.port.in.WholesalerUseCase;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.B2BRestockOrderPort;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.WholesalerPort;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.model.DarkStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class WholesalerServiceImpl implements WholesalerUseCase {

    @Autowired
    private WholesalerPort wholesalerPort;

    @Autowired
    private B2BRestockOrderPort restockOrderPort;

    @Autowired
    private LedgerUseCase ledgerService;

    @Override
    public List<B2BRestockOrder> getAssignedRestocks(String wholesalerId) {
        wholesalerPort.findById(wholesalerId)
                .orElseThrow(() -> new NoSuchElementException("Wholesaler not found: " + wholesalerId));
        return restockOrderPort.findByWholesalerId(wholesalerId);
    }

    @Override
    @Transactional
    public B2BRestockOrder createRestockOrder(String storeId, String preferredWholesalerId, String idempotencyKey) {
        // Idempotency guard
        if (idempotencyKey != null) {
            Optional<B2BRestockOrder> existing = restockOrderPort.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // Select primary wholesaler, fallback if primary is inactive or low trust
        Wholesaler selected;
        boolean isFallback = false;

        if (preferredWholesalerId != null) {
            selected = wholesalerPort.findById(preferredWholesalerId)
                    .orElseThrow(() -> new NoSuchElementException("Wholesaler not found: " + preferredWholesalerId));
        } else {
            selected = wholesalerPort.findByIsPrimary(true)
                    .orElseThrow(() -> new NoSuchElementException("No primary wholesaler configured."));
        }

        final String currentSelectedId = selected.getWholesalerId();
        // Check if primary wholesaler is eligible (trust >= 60 and active)
        if (!selected.getIsActive() || selected.getTrustScore() < 60) {
            // Switch to fallback wholesaler
            selected = wholesalerPort.findAll().stream()
                    .filter(w -> !w.getWholesalerId().equals(currentSelectedId))
                    .filter(Wholesaler::getIsActive)
                    .filter(w -> w.getTrustScore() >= 60)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No eligible wholesaler available for restock."));
            isFallback = true;
        }

        // Calculate invoice amount with academy discount
        BigDecimal invoiceAmount = isFallback ? selected.getFallbackInvoiceAmount() : selected.getBaseInvoiceAmount();
        if (selected.getAcademyDiscountActive()) {
            invoiceAmount = invoiceAmount.multiply(new BigDecimal("0.90")); // 10% discount
        }

        B2BRestockOrder restockOrder = B2BRestockOrder.builder()
                .store(DarkStore.builder().storeId(storeId).build()) // Reference only
                .wholesaler(selected)
                .invoiceAmount(invoiceAmount)
                .isFallback(isFallback)
                .idempotencyKey(idempotencyKey)
                .build();

        return restockOrderPort.save(restockOrder);
    }

    @Override
    @Transactional
    public Map<String, Object> fulfillRestock(Integer restockOrderId) {
        B2BRestockOrder restock = restockOrderPort.findById(restockOrderId)
                .orElseThrow(() -> new NoSuchElementException("Restock order not found: " + restockOrderId));

        if (!"pending".equalsIgnoreCase(restock.getStatus())) {
            throw new IllegalStateException("Restock order is not pending. Current: " + restock.getStatus());
        }

        restock.setStatus("fulfilled");
        restockOrderPort.save(restock);

        // Record B2B payment in double-entry ledger
        List<LedgerUseCase.LedgerLeg> legs = List.of(
                new LedgerUseCase.LedgerLeg("system", null, restock.getInvoiceAmount(), BigDecimal.ZERO),
                new LedgerUseCase.LedgerLeg("wholesaler", restock.getWholesaler().getWholesalerId(),
                        BigDecimal.ZERO, restock.getInvoiceAmount())
        );
        ledgerService.recordTransaction("B2B-RESTOCK-PAY",
                "Restock fulfillment payment to wholesaler " + restock.getWholesaler().getName(), legs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "fulfilled");
        result.put("restockOrderId", restockOrderId);
        result.put("wholesaler", restock.getWholesaler().getName());
        result.put("invoiceAmount", restock.getInvoiceAmount());
        result.put("isFallback", restock.getIsFallback());
        return result;
    }

    @Override
    public Map<String, Object> getInvoiceSummary(String wholesalerId) {
        Wholesaler wholesaler = wholesalerPort.findById(wholesalerId)
                .orElseThrow(() -> new NoSuchElementException("Wholesaler not found: " + wholesalerId));

        List<B2BRestockOrder> allOrders = restockOrderPort.findByWholesalerId(wholesalerId);

        BigDecimal totalInvoiced = allOrders.stream()
                .filter(o -> "fulfilled".equalsIgnoreCase(o.getStatus()))
                .map(B2BRestockOrder::getInvoiceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingCount = allOrders.stream()
                .filter(o -> "pending".equalsIgnoreCase(o.getStatus()))
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wholesalerId", wholesalerId);
        result.put("wholesalerName", wholesaler.getName());
        result.put("totalFulfilledInvoiceAmount", totalInvoiced);
        result.put("pendingOrderCount", pendingCount);
        result.put("totalOrderCount", allOrders.size());
        result.put("academyDiscountActive", wholesaler.getAcademyDiscountActive());
        result.put("trustScore", wholesaler.getTrustScore());
        return result;
    }
}
