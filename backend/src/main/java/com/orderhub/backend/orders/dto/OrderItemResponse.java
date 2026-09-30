package com.orderhub.backend.orders.dto;

import java.math.BigDecimal;

public record OrderItemResponse(Long productId, String productName, Integer quantity, BigDecimal unitPrice) {
}