package com.bbd.sales.application.result;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 목록 item 결과. */
public record SalesOrderSummaryResult(
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
        BigDecimal totalAmount,
        int itemCount,       // #67: 주문 라인(품목) 수 — 모바일 카드 "N품목"
        int totalQuantity,   // #67: 전 라인 수량 합 — "총 M개"
        String note
) {
}
