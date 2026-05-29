package com.bbd.sales.application.result;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 상세 결과. (이전엔 web 의 SalesOrderLineResponse 를 import 했었음 -> SalesOrderLineResult 로 교정) */
public record SalesOrderResult(
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
        String rejectReason,
        BigDecimal totalAmount,
        String note,
        List<SalesOrderLineResult> lines
) {
}
