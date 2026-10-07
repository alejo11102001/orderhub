package com.orderhub.backend.catalog;

import com.orderhub.backend.auth.Role;
import com.orderhub.backend.auth.User;
import com.orderhub.backend.auth.UserRepository;
import com.orderhub.backend.orders.OrderService;
import com.orderhub.backend.orders.dto.CreateOrderRequest;
import com.orderhub.backend.orders.dto.OrderItemRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductDeleteConflictIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired OrderService orderService;

    private Product newProduct() {
        Product p = new Product();
        p.setName("P-" + UUID.randomUUID());
        p.setPrice(new BigDecimal("10.00"));
        p.setStock(5);
        return productRepository.save(p);
    }

    @Test
    void deletingProductWithOrders_returns409_andKeepsProduct() throws Exception {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@test.com");
        u.setPasswordHash("x");
        u.setRole(Role.CUSTOMER);
        String email = userRepository.save(u).getEmail();
        Product p = newProduct();
        orderService.create(email, new CreateOrderRequest(List.of(new OrderItemRequest(p.getId(), 1))));

        mockMvc.perform(delete("/api/products/" + p.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("referenced")));

        assertThat(productRepository.existsById(p.getId())).isTrue();
    }

    @Test
    void deletingProductWithoutOrders_stillReturns204() throws Exception {
        Product p = newProduct();

        mockMvc.perform(delete("/api/products/" + p.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());

        assertThat(productRepository.existsById(p.getId())).isFalse();
    }
}
