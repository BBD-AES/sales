package com.bbd.sales.adapter.in.web.dto;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 상세 응답. */
public record SalesOrderDetailResponse(
        String soNumber,
        String fromWarehouseCode,
        String fromWarehouseName,
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
        List<SalesOrderLineResponse> lines
) {
}
