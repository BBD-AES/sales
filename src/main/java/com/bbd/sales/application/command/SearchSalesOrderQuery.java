package com.bbd.sales.application.command;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;

import java.time.LocalDate;

/**
 * 목록 조회 입력값.
 */
public record SearchSalesOrderQuery(
        SalesOrderStatus status,
        SalesOrderPriority priority,
        String toWarehouseCode,
        String requestedBy,
        String receivedBy,
        LocalDate startDate,
        LocalDate endDate,
        int page,
        int size
) {
}
