package com.orderhub.backend.orders.dto;

import com.orderhub.backend.orders.Order;
import com.orderhub.backend.orders.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal total,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(), o.getStatus(), o.getTotal(), o.getCreatedAt(),
                o.getItems().stream()
                        .map(i -> new OrderItemResponse(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                        .toList());
    }
}