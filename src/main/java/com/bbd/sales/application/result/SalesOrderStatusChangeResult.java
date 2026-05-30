package com.bbd.sales.application.result;

import com.bbd.sales.domain.SalesOrderStatus;

import java.time.LocalDateTime;

/** 취소/승인/반려/수령 결과. */
public record SalesOrderStatusChangeResult(
        String soNumber,
        SalesOrderStatus status,
        String actor,
        LocalDateTime changedAt,
        String reason
) {
}
