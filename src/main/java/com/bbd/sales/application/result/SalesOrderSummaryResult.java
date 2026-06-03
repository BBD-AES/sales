package com.bbd.sales.application.result;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 목록 item 결과. */
public record SalesOrderSummaryResult(
        String soNumber,
        String fromWarehouseCode,
        String fromWarehouseName,
        SalesOrderStatus status,
        SalesOrderPriority priority,
        String requestedBy,
        String approvedBy,
        String receivedBy,
        String canceledBy,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt,
        LocalDateTime receivedAt,
        LocalDateTime canceledAt,
        BigDecimal totalAmount,
        String note
) {
}
