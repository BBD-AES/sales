package com.bbd.sales.adapter.in.web.dto;

import com.bbd.sales.domain.CustomerOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CustomerOrderDetailResponse(
        String coNumber, String dealerWarehouseCode, String customerName, String customerContact, CustomerOrderStatus status,
        String requestedBy, String confirmedBy, String canceledBy, String closedBy,
        LocalDateTime requestedAt, LocalDateTime confirmedAt, LocalDateTime canceledAt, LocalDateTime closedAt,
        BigDecimal totalAmount, String note, List<CustomerOrderLineResponse> lines
) {
}
