package ch.swissqcommerce.backend.integration;

import ch.swissqcommerce.backend.domain.wholesaler.adapter.in.web.WholesalerController;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.B2BRestockOrderRepository;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.PurchaseOrderRepository;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.WastageLogEntityRepository;
import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.WholesalerRepository;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.WastageLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class WholesalerIntegrationTest {

    @Autowired
    private WholesalerController wholesalerController;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private WastageLogEntityRepository wastageLogRepository;

    @Autowired
    private B2BRestockOrderRepository b2BRestockOrderRepository;

    @Autowired
    private WholesalerRepository wholesalerRepository;

    /**
     * Clean all wholesaler-related tables before each test so that no stale data
     * from a prior test (or from concurrent test runs) leaks into assertions.
     */
    @BeforeEach
    void setUp() {
        wastageLogRepository.deleteAll();
        purchaseOrderRepository.deleteAll();
        b2BRestockOrderRepository.deleteAll();
        wholesalerRepository.deleteAll();
    }

    @Test
    public void testReplenishmentAndWastageFlow() {
        // 1. Generate PO
        WholesalerController.GeneratePoRequest poReq = new WholesalerController.GeneratePoRequest();
        poReq.setStoreId("STORE-123");
        poReq.setVendorName("Global Suppliers Inc.");
        Map<String, Integer> items = new HashMap<>();
        items.put("PROD-MILK", 50);
        items.put("PROD-BREAD", 100);
        poReq.setRequestedItems(items);

        PurchaseOrder po = wholesalerController.generateReplenishmentOrders(poReq).getBody();
        assertNotNull(po);
        assertEquals("DRAFT", po.getStatus());
        assertEquals(2, po.getItems().size());
        assertEquals(0, po.getItems().get(0).getReceivedQty());

        // Verify PO was persisted
        assertTrue(purchaseOrderRepository.existsById(po.getPoId()),
                "PO should be persisted in database");

        // 2. Receive Goods (Partial)
        WholesalerController.ReceiveGoodsRequest recvReq = new WholesalerController.ReceiveGoodsRequest();
        Map<String, Integer> receipts = new HashMap<>();
        receipts.put("PROD-MILK", 50);  // Fully received
        receipts.put("PROD-BREAD", 80); // Missing 20
        recvReq.setItemReceipts(receipts);
        recvReq.setGrnFileUrl("https://example.com/grn/123.pdf");

        PurchaseOrder updatedPo = wholesalerController.receiveGoods(po.getPoId(), recvReq).getBody();
        assertNotNull(updatedPo);
        assertEquals("PARTIALLY_RECEIVED", updatedPo.getStatus());
        assertEquals("https://example.com/grn/123.pdf", updatedPo.getGrnVerificationFileUrl());

        // 3. Log Wastage
        WholesalerController.LogWastageRequest wasteReq = new WholesalerController.LogWastageRequest();
        wasteReq.setStoreId("STORE-123");
        wasteReq.setProductId("PROD-MILK");
        wasteReq.setBatchId("BATCH-001");
        wasteReq.setQty(5);
        wasteReq.setReason("EXPIRED");
        wasteReq.setLoggedBy("EMP-456");

        WastageLog log = wholesalerController.logWastage(wasteReq).getBody();
        assertNotNull(log);
        assertEquals("EXPIRED", log.getReason());
        assertEquals(5, log.getQtyWasted());

        // Verify wastage was persisted
        assertEquals(1, wastageLogRepository.count(),
                "Exactly one wastage log should be in the database");
    }

    @Test
    public void testGenerateReplenishmentOrder_createsDraftWithCorrectItemCount() {
        WholesalerController.GeneratePoRequest poReq = new WholesalerController.GeneratePoRequest();
        poReq.setStoreId("STORE-ZH-HB");
        poReq.setVendorName("Swiss Organic Supplies AG");
        Map<String, Integer> items = new HashMap<>();
        items.put("SKU-EGGS", 200);
        poReq.setRequestedItems(items);

        PurchaseOrder po = wholesalerController.generateReplenishmentOrders(poReq).getBody();

        assertNotNull(po);
        assertNotNull(po.getPoId());
        assertEquals("DRAFT", po.getStatus());
        assertEquals(1, po.getItems().size());
        assertEquals("SKU-EGGS", po.getItems().get(0).getProductId());
        assertEquals(200, po.getItems().get(0).getRequestedQty());
        assertEquals(0, po.getItems().get(0).getReceivedQty());
    }

    @Test
    public void testReceiveGoods_fullyReceivedChangesStatusToReceived() {
        // Setup: create PO with a single item
        WholesalerController.GeneratePoRequest poReq = new WholesalerController.GeneratePoRequest();
        poReq.setStoreId("STORE-BERN");
        poReq.setVendorName("Bern Dairy Co.");
        Map<String, Integer> items = new HashMap<>();
        items.put("PROD-BUTTER", 30);
        poReq.setRequestedItems(items);
        PurchaseOrder po = wholesalerController.generateReplenishmentOrders(poReq).getBody();

        // Receive all 30 units
        WholesalerController.ReceiveGoodsRequest recvReq = new WholesalerController.ReceiveGoodsRequest();
        Map<String, Integer> receipts = new HashMap<>();
        receipts.put("PROD-BUTTER", 30);
        recvReq.setItemReceipts(receipts);
        recvReq.setGrnFileUrl("https://grn.example.com/butter.pdf");

        PurchaseOrder updatedPo = wholesalerController.receiveGoods(po.getPoId(), recvReq).getBody();
        assertNotNull(updatedPo);
        assertEquals("RECEIVED", updatedPo.getStatus());
        assertEquals(30, updatedPo.getItems().get(0).getReceivedQty());
    }
}
