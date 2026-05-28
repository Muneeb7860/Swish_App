package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * B2B wholesaler domain service managing restock order lifecycle,
 * invoice calculations with academy discount and fallback supplier logic,
 * and order fulfillment processing.
 */
@Service
public class WholesalerService {

    @Autowired
    private WholesalerRepository wholesalerRepository;

    @Autowired
    private B2BRestockOrderRepository restockOrderRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private LedgerUseCase ledgerService;

    /**
     * Returns all restock orders assigned to a given wholesaler.
     */
    public List<B2BRestockOrder> getAssignedRestocks(String wholesalerId) {
        wholesalerRepository.findById(wholesalerId)
                .orElseThrow(() -> new NoSuchElementException("Wholesaler not found: " + wholesalerId));
        return restockOrderRepository.findByWholesalerWholesalerIdOrderByCreatedAtDesc(wholesalerId);
    }

    /**
     * Creates a new B2B restock order.
     * Implements F13 primary/fallback wholesaler selection and F14 academy discount pricing.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public B2BRestockOrder createRestockOrder(String storeId, String preferredWholesalerId, String idempotencyKey) {
        // Idempotency guard
        if (idempotencyKey != null) {
            Optional<B2BRestockOrder> existing = restockOrderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // F13: Select primary wholesaler, fallback if primary is inactive or low trust
        Wholesaler selected;
        boolean isFallback = false;

        if (preferredWholesalerId != null) {
            selected = wholesalerRepository.findById(preferredWholesalerId)
                    .orElseThrow(() -> new NoSuchElementException("Wholesaler not found: " + preferredWholesalerId));
        } else {
            selected = wholesalerRepository.findByIsPrimary(true)
                    .orElseThrow(() -> new NoSuchElementException("No primary wholesaler configured."));
        }

        // Check if primary wholesaler is eligible (trust >= 60 and active)
        if (!selected.getIsActive() || selected.getTrustScore() < 60) {
            // Switch to fallback wholesaler
            selected = wholesalerRepository.findAll().stream()
                    .filter(w -> !w.getWholesalerId().equals(preferredWholesalerId))
                    .filter(Wholesaler::getIsActive)
                    .filter(w -> w.getTrustScore() >= 60)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No eligible wholesaler available for restock."));
            isFallback = true;
        }

        // Calculate invoice amount with F14 academy discount
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

        return restockOrderRepository.save(restockOrder);
    }

    /**
     * Fulfills a restock order, updates its status, and records the B2B payment in the ledger.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Map<String, Object> fulfillRestock(Integer restockOrderId) {
        B2BRestockOrder restock = restockOrderRepository.findById(restockOrderId)
                .orElseThrow(() -> new NoSuchElementException("Restock order not found: " + restockOrderId));

        if (!"pending".equalsIgnoreCase(restock.getStatus())) {
            throw new IllegalStateException("Restock order is not pending. Current: " + restock.getStatus());
        }

        restock.setStatus("fulfilled");
        restockOrderRepository.save(restock);

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

    /**
     * Returns invoice summary for a wholesaler's fulfilled orders.
     */
    public Map<String, Object> getInvoiceSummary(String wholesalerId) {
        Wholesaler wholesaler = wholesalerRepository.findById(wholesalerId)
                .orElseThrow(() -> new NoSuchElementException("Wholesaler not found: " + wholesalerId));

        List<B2BRestockOrder> allOrders = restockOrderRepository
                .findByWholesalerWholesalerIdOrderByCreatedAtDesc(wholesalerId);

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
