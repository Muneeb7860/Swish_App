package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.domain.inventory.adapter.in.web.InventoryController;
import ch.swissqcommerce.backend.domain.inventory.core.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
public class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Test
    public void testGetPickerQueue() throws Exception {
        when(inventoryService.getPickerQueue()).thenReturn(List.of("Order-1", "Order-2"));

        mockMvc.perform(get("/api/inventory/picker/queue"))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"Order-1\",\"Order-2\"]"));
    }

    @Test
    public void testHandoverPicker() throws Exception {
        when(inventoryService.handoverPicker("Order-1")).thenReturn("Handover successful for Order-1");

        mockMvc.perform(post("/api/inventory/picker/handover")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"Order-1\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Handover successful for Order-1"));
    }

    @Test
    public void testRebalanceInventory() throws Exception {
        when(inventoryService.rebalanceInventory()).thenReturn("Inventory rebalanced successfully");

        mockMvc.perform(post("/api/inventory/rebalance"))
                .andExpect(status().isOk())
                .andExpect(content().string("Inventory rebalanced successfully"));
    }

    @Test
    public void testGetCatalog() throws Exception {
        when(inventoryService.getCatalog()).thenReturn(List.of("A", "B"));

        mockMvc.perform(get("/api/customer/catalog"))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"A\",\"B\"]"));
    }
}
