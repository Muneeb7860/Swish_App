package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.Inventory;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.InventoryRepository;
import ch.swissqcommerce.backend.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

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
    @MockBean private HitlQueueRepository hitlQueueRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testGetCatalog() throws Exception {
        when(inventoryRepository.findAll()).thenReturn(List.of(new Inventory()));
        mockMvc.perform(get("/api/customer/catalog"))
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
