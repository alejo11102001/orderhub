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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OrderConcurrencyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired OrderService orderService;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;

    @Test
    void tenBuyersCompeteForLastUnit_onlyOneWins() throws Exception {
        int buyers = 10;

        Product p = new Product();
        p.setName("Ultimo-" + UUID.randomUUID());
        p.setPrice(new BigDecimal("10.00"));
        p.setStock(1);
        Long productId = productRepository.save(p).getId();

        List<String> emails = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            User u = new User();
            u.setEmail(UUID.randomUUID() + "@test.com");
            u.setPasswordHash("x");
            u.setRole(Role.CUSTOMER);
            emails.add(userRepository.save(u).getEmail());
        }

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (String email : emails) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    orderService.create(email,
                            new CreateOrderRequest(List.of(new OrderItemRequest(productId, 1))));
                    success.incrementAndGet();
                } catch (InsufficientStockException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(buyers - 1);
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isZero();
    }
}