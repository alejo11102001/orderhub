package com.orderhub.backend.orders;

import com.orderhub.backend.auth.Role;
import com.orderhub.backend.auth.User;
import com.orderhub.backend.auth.UserRepository;
import com.orderhub.backend.catalog.Product;
import com.orderhub.backend.catalog.ProductRepository;
import com.orderhub.backend.orders.dto.CreateOrderRequest;
import com.orderhub.backend.orders.dto.OrderItemRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-32b",
        "app.jwt.expiration-minutes=5"
})
class OrderFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;

    private String newUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@test.com");
        u.setPasswordHash("x");
        u.setRole(Role.CUSTOMER);
        return userRepository.save(u).getEmail();
    }

    private Product newProduct(int stock) {
        Product p = new Product();
        p.setName("P-" + UUID.randomUUID());
        p.setPrice(new BigDecimal("10.00"));
        p.setStock(stock);
        return productRepository.save(p);
    }

    private CreateOrderRequest req(Long productId, int qty) {
        return new CreateOrderRequest(List.of(new OrderItemRequest(productId, qty)));
    }

    @Test
    void cancel_persistsStatus_restoresStock_andSecondCancelFails() {
        String email = newUser();
        Product p = newProduct(5);

        Long orderId = orderService.create(email, req(p.getId(), 2)).id();
        assertThat(productRepository.findById(p.getId()).orElseThrow().getStock()).isEqualTo(3);

        orderService.cancel(orderId, email, false);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(productRepository.findById(p.getId()).orElseThrow().getStock()).isEqualTo(5);

        assertThatThrownBy(() -> orderService.cancel(orderId, email, false))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThat(productRepository.findById(p.getId()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void create_withInsufficientStock_rollsBackEarlierLines() {
        String email = newUser();
        Product ok = newProduct(5);
        Product scarce = newProduct(1);

        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(ok.getId(), 2),
                new OrderItemRequest(scarce.getId(), 5)));

        assertThatThrownBy(() -> orderService.create(email, request))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(productRepository.findById(ok.getId()).orElseThrow().getStock()).isEqualTo(5);
    }
}