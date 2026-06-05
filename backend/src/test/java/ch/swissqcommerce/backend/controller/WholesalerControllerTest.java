package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.domain.wholesaler.adapter.in.web.WholesalerController;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.domain.wholesaler.port.in.WholesalerUseCase;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WholesalerController.class)
@AutoConfigureMockMvc(addFilters = false)
public class WholesalerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WholesalerUseCase wholesalerService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testGetAssignedRestocks() throws Exception {
        when(wholesalerService.getAssignedRestocks("WHOLESALER-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/wholesaler/restocks?id=WHOLESALER-1"))
                .andExpect(status().isOk());
    }

    @Test
    public void testCreateRestockOrder() throws Exception {
        WholesalerController.CreateRestockRequest req = new WholesalerController.CreateRestockRequest();
        req.setStoreId("STORE-1");
        req.setPreferredWholesalerId("WHOLESALER-2");

        B2BRestockOrder order = new B2BRestockOrder();
        order.setRestockOrderId(10);
        DarkStore store = new DarkStore();
        store.setStoreId("STORE-1");
        order.setStore(store);

        when(wholesalerService.createRestockOrder(eq("STORE-1"), eq("WHOLESALER-2"), any()))
                .thenReturn(order);

        mockMvc.perform(post("/api/wholesaler/restocks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restockOrderId").value(10))
                .andExpect(jsonPath("$.store.storeId").value("STORE-1"));
    }

    @Test
    public void testFulfillRestock() throws Exception {
        when(wholesalerService.fulfillRestock(10))
                .thenReturn(Map.of("status", "fulfilled"));

        mockMvc.perform(post("/api/wholesaler/restocks/10/fulfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("fulfilled"));
    }

    @Test
    public void testGetInvoiceSummary() throws Exception {
        when(wholesalerService.getInvoiceSummary("WHOLESALER-1"))
                .thenReturn(Map.of("total", 1000));

        mockMvc.perform(get("/api/wholesaler/invoices?id=WHOLESALER-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1000));
    }
}
