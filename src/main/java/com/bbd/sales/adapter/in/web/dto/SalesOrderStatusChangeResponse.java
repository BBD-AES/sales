package com.bbd.sales.adapter.in.web.dto;

import com.bbd.sales.domain.SalesOrderStatus;

import java.time.LocalDateTime;

/** 취소/승인/반려/수령 상태 변경 응답. */
public record SalesOrderStatusChangeResponse(
        String soNumber,
        SalesOrderStatus status,
        String actor,
        LocalDateTime changedAt,
        String reason
) {
}
