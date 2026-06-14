package com.bbd.sales.application.result;

import com.bbd.sales.domain.CustomerOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CustomerOrderResult(
        String coNumber, String dealerWarehouseCode, String dealerName,
        String customerName, String customerContact, CustomerOrderStatus status,
        String requestedBy, String confirmedBy, String canceledBy, String closedBy,
        LocalDateTime requestedAt, LocalDateTime confirmedAt, LocalDateTime canceledAt, LocalDateTime closedAt,
        BigDecimal totalAmount, String note, List<CustomerOrderLineResult> lines
) {
}
