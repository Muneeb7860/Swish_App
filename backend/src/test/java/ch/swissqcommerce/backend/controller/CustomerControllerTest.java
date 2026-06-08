package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.domain.customer.adapter.in.web.CustomerController;
import ch.swissqcommerce.backend.domain.customer.port.in.CustomerUseCase;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.InventoryRepository;
import ch.swissqcommerce.backend.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.when;
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
    @MockBean private CustomerUseCase customerUseCase;
    @MockBean private HitlQueueRepository hitlQueueRepository;

    private SecurityContext originalContext;

    @BeforeEach
    public void setUp() {
        originalContext = SecurityContextHolder.getContext();
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.setContext(originalContext);
    }

    @Test
    public void testPurgeProfile_Unauthorized() throws Exception {
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        mockMvc.perform(post("/api/customer/profile/purge")
                .param("customerId", "cust123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testPurgeProfile_Forbidden() throws Exception {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getName()).thenReturn("otherUser");
        when(auth.getAuthorities()).thenReturn(Collections.emptyList());

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        mockMvc.perform(post("/api/customer/profile/purge")
                .param("customerId", "cust123"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testPurgeProfile_NotFound() throws Exception {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getName()).thenReturn("cust123");
        when(auth.getAuthorities()).thenReturn(Collections.emptyList());

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        when(customerUseCase.purgeProfile("cust123")).thenThrow(new java.util.NoSuchElementException("Customer not found."));

        mockMvc.perform(post("/api/customer/profile/purge")
                .param("customerId", "cust123"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testPurgeProfile_Success() throws Exception {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getName()).thenReturn("cust123");
        when(auth.getAuthorities()).thenReturn(Collections.emptyList());

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        when(customerUseCase.purgeProfile("cust123")).thenReturn(java.util.Map.of(
                "status", "purged",
                "probationary_trust_score", 75
        ));

        mockMvc.perform(post("/api/customer/profile/purge")
                .param("customerId", "cust123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("purged"))
                .andExpect(jsonPath("$.probationary_trust_score").value(75));
    }

    @Test
    public void testPurgeProfile_Success_AdminOverride() throws Exception {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getName()).thenReturn("adminUser");
        
        GrantedAuthority adminAuthority = new SimpleGrantedAuthority("ROLE_ADMIN");
        when(auth.getAuthorities()).thenAnswer(inv -> Collections.singletonList(adminAuthority));

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        when(customerUseCase.purgeProfile("cust123")).thenReturn(java.util.Map.of(
                "status", "purged",
                "probationary_trust_score", 75
        ));

        mockMvc.perform(post("/api/customer/profile/purge")
                .param("customerId", "cust123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("purged"));
    }
}
