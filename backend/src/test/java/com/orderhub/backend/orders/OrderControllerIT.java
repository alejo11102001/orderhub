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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired OrderService orderService;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;

    private String newUser(Role role) {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@test.com");
        u.setPasswordHash("x");
        u.setRole(role);
        return userRepository.save(u).getEmail();
    }

    private Product newProduct(int stock) {
        Product p = new Product();
        p.setName("P-" + UUID.randomUUID());
        p.setPrice(new BigDecimal("10.00"));
        p.setStock(stock);
        return productRepository.save(p);
    }

    private Long newOrder(String email, Product p) {
        return orderService.create(email,
                new CreateOrderRequest(List.of(new OrderItemRequest(p.getId(), 1)))).id();
    }

    private static RequestPostProcessor as(String email, String role) {
        return jwt().jwt(j -> j.subject(email)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerSeesOnlyTheirOwnOrders() throws Exception {
        String ana = newUser(Role.CUSTOMER);
        String luis = newUser(Role.CUSTOMER);
        Product p = newProduct(10);
        Long anaOrder = newOrder(ana, p);
        Long luisOrder = newOrder(luis, p);

        mockMvc.perform(get("/api/orders").with(as(ana, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(anaOrder.intValue()))
                .andExpect(jsonPath("$.content[*].id", not(hasItems(luisOrder.intValue()))));
    }

    @Test
    void adminSeesEveryonesOrders() throws Exception {
        String ana = newUser(Role.CUSTOMER);
        String luis = newUser(Role.CUSTOMER);
        String admin = newUser(Role.ADMIN);
        Product p = newProduct(10);
        Long anaOrder = newOrder(ana, p);
        Long luisOrder = newOrder(luis, p);

        mockMvc.perform(get("/api/orders").param("size", "100").with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItems(anaOrder.intValue(), luisOrder.intValue())));
    }

    @Test
    void anotherUsersOrder_returns404_forGetPayAndCancel() throws Exception {
        String owner = newUser(Role.CUSTOMER);
        String intruder = newUser(Role.CUSTOMER);
        Long id = newOrder(owner, newProduct(5));

        mockMvc.perform(get("/api/orders/" + id).with(as(intruder, "CUSTOMER"))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/" + id + "/pay").with(as(intruder, "CUSTOMER"))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/" + id + "/cancel").with(as(intruder, "CUSTOMER"))).andExpect(status().isNotFound());

        // el dueño sigue viéndolo intacto
        mockMvc.perform(get("/api/orders/" + id).with(as(owner, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void adminCanReadAndPayAnyOrder() throws Exception {
        String owner = newUser(Role.CUSTOMER);
        String admin = newUser(Role.ADMIN);
        Long id = newOrder(owner, newProduct(5));

        mockMvc.perform(get("/api/orders/" + id).with(as(admin, "ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + id + "/pay").with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void unknownOrder_returns404() throws Exception {
        mockMvc.perform(get("/api/orders/999999").with(as(newUser(Role.CUSTOMER), "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void payAndCancel_followTheStateMachine_andInvalidTransitionsReturn409() throws Exception {
        String email = newUser(Role.CUSTOMER);
        Long paid = newOrder(email, newProduct(5));
        Long cancelled = newOrder(email, newProduct(5));

        mockMvc.perform(post("/api/orders/" + paid + "/pay").with(as(email, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(post("/api/orders/" + cancelled + "/cancel").with(as(email, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        // PAID: ni pagar ni cancelar
        mockMvc.perform(post("/api/orders/" + paid + "/pay").with(as(email, "CUSTOMER"))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/orders/" + paid + "/cancel").with(as(email, "CUSTOMER"))).andExpect(status().isConflict());
        // CANCELLED: ni pagar ni cancelar otra vez
        mockMvc.perform(post("/api/orders/" + cancelled + "/pay").with(as(email, "CUSTOMER"))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/orders/" + cancelled + "/cancel").with(as(email, "CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only PENDING orders can be cancelled"));
    }

    @Test
    void create_validatesAndMapsErrors() throws Exception {
        String email = newUser(Role.CUSTOMER);
        Product scarce = newProduct(1);

        mockMvc.perform(post("/api/orders").with(as(email, "CUSTOMER")).contentType("application/json")
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders").with(as(email, "CUSTOMER")).contentType("application/json")
                        .content("{\"items\":[{\"productId\":" + scarce.getId() + ",\"quantity\":0}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders").with(as(email, "CUSTOMER")).contentType("application/json")
                        .content("{\"items\":[{\"productId\":999999,\"quantity\":1}]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders").with(as(email, "CUSTOMER")).contentType("application/json")
                        .content("{\"items\":[{\"productId\":" + scarce.getId() + ",\"quantity\":2}]}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/orders").with(as(email, "CUSTOMER")).contentType("application/json")
                        .content("{\"items\":[{\"productId\":" + scarce.getId() + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(10.0));
    }
}
