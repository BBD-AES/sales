package com.bbd.sales.adapter.in.web.dto;

import com.bbd.sales.domain.SalesOrderPriority;
import jakarta.validation.Valid;

import java.util.List;

/** PUT 수정 요청 body. lines == null 이면 라인 변경 없음. */
public record UpdateSalesOrderRequest(
        SalesOrderPriority priority,
        String note,
        List<@Valid SalesOrderLineRequest> lines
) {
}
