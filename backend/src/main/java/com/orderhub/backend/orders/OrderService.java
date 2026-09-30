package com.orderhub.backend.orders;

import com.orderhub.backend.auth.UserRepository;
import com.orderhub.backend.catalog.Product;
import com.orderhub.backend.catalog.ProductNotFoundException;
import com.orderhub.backend.catalog.ProductRepository;
import com.orderhub.backend.orders.dto.CreateOrderRequest;
import com.orderhub.backend.orders.dto.OrderItemRequest;
import com.orderhub.backend.orders.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public OrderResponse create(String email, CreateOrderRequest request) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"))
                .getId();

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest line : request.items()) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new ProductNotFoundException(line.productId()));

            if (productRepository.decrementStock(product.getId(), line.quantity()) == 0) {
                throw new InsufficientStockException(product.getId());
            }

            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setQuantity(line.quantity());
            item.setUnitPrice(product.getPrice());
            order.addItem(item);

            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        order.setTotal(total);
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(String email, boolean isAdmin, Pageable pageable) {
        if (isAdmin) {
            return orderRepository.findAll(pageable).map(OrderResponse::from);
        }
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        return orderRepository.findByUserId(userId, pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id, String email, boolean isAdmin) {
        return OrderResponse.from(loadAuthorized(id, email, isAdmin));
    }

    @Transactional
    public OrderResponse pay(Long id, String email, boolean isAdmin) {
        Order order = loadAuthorized(id, email, isAdmin);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Only PENDING orders can be paid");
        }
        order.setStatus(OrderStatus.PAID);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(Long id, String email, boolean isAdmin) {
        Order order = loadAuthorized(id, email, isAdmin);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Only PENDING orders can be cancelled");
        }
        order.getItems().forEach(i -> productRepository.incrementStock(i.getProductId(), i.getQuantity()));
        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(order);
    }

    private Order loadAuthorized(Long id, String email, boolean isAdmin) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        if (!isAdmin) {
            Long userId = userRepository.findByEmail(email).orElseThrow().getId();
            if (!order.getUserId().equals(userId)) {
                // 404 y no 403: no revelamos que el pedido existe
                throw new OrderNotFoundException(id);
            }
        }
        return order;
    }
}