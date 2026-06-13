package ch.swissqcommerce.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swissqcommerce.backend.domain.inventory.adapter.in.web.InventoryController;
import ch.swissqcommerce.backend.domain.inventory.port.in.StockManagementUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
public class InventoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private StockManagementUseCase stockManagementUseCase;

    @Test
    public void testReserveStock() throws Exception {
        when(stockManagementUseCase.reserveStock("SKU-123", 5)).thenReturn(null);

        mockMvc.perform(post("/api/v1/inventory/SKU-123/reserve").param("amount", "5"))
                .andExpect(status().isOk());
    }
}
