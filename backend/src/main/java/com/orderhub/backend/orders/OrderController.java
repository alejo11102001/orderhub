package com.orderhub.backend.orders;

import com.orderhub.backend.orders.dto.CreateOrderRequest;
import com.orderhub.backend.orders.dto.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(Authentication auth, @Valid @RequestBody CreateOrderRequest request) {
        return service.create(auth.getName(), request);
    }

    @GetMapping
    public Page<OrderResponse> list(Authentication auth, Pageable pageable) {
        return service.list(auth.getName(), isAdmin(auth), pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse get(Authentication auth, @PathVariable Long id) {
        return service.get(id, auth.getName(), isAdmin(auth));
    }

    @PostMapping("/{id}/pay")
    public OrderResponse pay(Authentication auth, @PathVariable Long id) {
        return service.pay(id, auth.getName(), isAdmin(auth));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(Authentication auth, @PathVariable Long id) {
        return service.cancel(id, auth.getName(), isAdmin(auth));
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}