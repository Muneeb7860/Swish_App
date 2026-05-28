package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.service.LedgerService;
import ch.swissqcommerce.backend.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private InventoryRepository inventoryRepository;
    @MockBean private OrderRepository orderRepository;
    @MockBean private CustomerRepository customerRepository;
    @MockBean private OrderService orderService;
    @MockBean private HitlQueueRepository hitlQueueRepository;
    @MockBean private LedgerService ledgerService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testGetCatalog() throws Exception {
        when(inventoryRepository.findAll()).thenReturn(List.of(new Inventory()));
        mockMvc.perform(get("/api/customer/catalog"))
                .andExpect(status().isOk());
    }

    @Test
    public void testPlaceOrder_InvalidPayload() throws Exception {
        CustomerController.OrderRequest req = new CustomerController.OrderRequest();
        
        mockMvc.perform(post("/api/customer/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testGetCustomerLedger() throws Exception {
        when(ledgerService.getCustomerLedger("C1")).thenReturn(List.of());
        mockMvc.perform(get("/api/customer/ledger?customerId=C1"))
                .andExpect(status().isOk());
    }

    @Test
    public void testPurgeProfile() throws Exception {
        Customer c = new Customer();
        c.setCustomerId("C1");
        c.setTrustScore(90);
        c.setAddresses(new java.util.ArrayList<>());
        c.setPaymentCards(new java.util.ArrayList<>());
        when(customerRepository.findById("C1")).thenReturn(Optional.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/customer/profile/purge?customerId=C1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("purged"));
    }
}
