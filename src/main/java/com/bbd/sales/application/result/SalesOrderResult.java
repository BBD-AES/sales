package com.bbd.sales.application.result;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SalesOrderResult(
        String soNumber,
        String toWarehouseCode,
        String toWarehouseName,
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
        String rejectedReason,
        BigDecimal totalAmount,
        String note,
        String customerOrderNumber,
        List<SalesOrderLineResult> lines
) {
}
