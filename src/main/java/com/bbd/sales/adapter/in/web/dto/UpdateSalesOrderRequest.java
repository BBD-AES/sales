package com.bbd.sales.adapter.in.web.dto;

import com.bbd.sales.domain.SalesOrderPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/** PUT 수정 요청 body. lines == null 이면 라인 변경 없음. */
public record UpdateSalesOrderRequest(
        SalesOrderPriority priority,
        String note,
        @Size(min = 1, message = "라인을 교체할 경우 최소 1개 이상이어야 합니다.")
        List<@Valid SalesOrderLineRequest> lines
) {
}
