package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
public class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testGetPickerQueue() throws Exception {
        when(inventoryService.getPickerQueue("STORE_1")).thenReturn(List.of());

        mockMvc.perform(get("/api/inventory/picker/queue?storeId=STORE_1"))
                .andExpect(status().isOk());
    }

    @Test
    public void testRebalanceStock() throws Exception {
        InventoryController.RebalanceRequest req = new InventoryController.RebalanceRequest();
        req.setItemId("ITEM_1");
        req.setFromStoreId("STORE_1");
        req.setToStoreId("STORE_2");
        req.setQuantity(10);

        when(inventoryService.rebalanceStock("ITEM_1", "STORE_1", "STORE_2", 10))
                .thenReturn(Map.of("status", "success"));

        mockMvc.perform(post("/api/inventory/rebalance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    public void testHandoverToRider() throws Exception {
        InventoryController.HandoverRequest req = new InventoryController.HandoverRequest();
        req.setOrderId(1);
        req.setPickerId("P1");
        req.setRiderId("R1");
        req.setDurationSeconds(120);

        when(inventoryService.handoverToRider(1, "P1", "R1", 120))
                .thenReturn(Map.of("status", "handed_over"));

        mockMvc.perform(post("/api/inventory/picker/handover")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("handed_over"));
    }
}
