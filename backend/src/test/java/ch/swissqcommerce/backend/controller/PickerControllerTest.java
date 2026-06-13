package ch.swissqcommerce.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swissqcommerce.backend.domain.inventory.adapter.in.web.PickerController;
import ch.swissqcommerce.backend.model.Picker;
import ch.swissqcommerce.backend.repository.PickerRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PickerController.class)
@AutoConfigureMockMvc(addFilters = false)
public class PickerControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PickerRepository pickerRepository;

    @Test
    public void testGetPickerQueue() throws Exception {
        Picker picker =
                Picker.builder()
                        .pickerId("picker-1")
                        .fullName("John Picker")
                        .trustScore(95)
                        .lightningBadge(true)
                        .build();
        when(pickerRepository.findAll()).thenReturn(List.of(picker));

        mockMvc.perform(get("/api/inventory/picker/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pickerId").value("picker-1"))
                .andExpect(jsonPath("$[0].fullName").value("John Picker"))
                .andExpect(jsonPath("$[0].trustScore").value(95))
                .andExpect(jsonPath("$[0].lightningBadge").value(true));
    }

    @Test
    public void testHandoverPicker() throws Exception {
        mockMvc.perform(
                        post("/api/inventory/picker/handover")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pickerId\":\"picker-1\",\"orderId\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickerId").value("picker-1"))
                .andExpect(jsonPath("$.orderId").value(123))
                .andExpect(jsonPath("$.status").value("HANDED_OVER"));
    }

    @Test
    public void testRebalanceInventory() throws Exception {
        mockMvc.perform(
                        post("/api/inventory/rebalance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"fromStoreId\":\"store-A\",\"toStoreId\":\"store-B\",\"sku\":\"SKU-1\",\"quantity\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromStoreId").value("store-A"))
                .andExpect(jsonPath("$.toStoreId").value("store-B"))
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.status").value("REBALANCE_QUEUED"));
    }
}
