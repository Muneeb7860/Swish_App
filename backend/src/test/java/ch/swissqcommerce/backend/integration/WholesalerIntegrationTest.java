package ch.swissqcommerce.backend.integration;

import ch.swissqcommerce.backend.domain.wholesaler.adapter.in.web.WholesalerController;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.WastageLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class WholesalerIntegrationTest {

    @Autowired
    private WholesalerController wholesalerController;

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

        // 2. Receive Goods (Partial)
        WholesalerController.ReceiveGoodsRequest recvReq = new WholesalerController.ReceiveGoodsRequest();
        Map<String, Integer> receipts = new HashMap<>();
        receipts.put("PROD-MILK", 50); // Fully received
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
    }
}
