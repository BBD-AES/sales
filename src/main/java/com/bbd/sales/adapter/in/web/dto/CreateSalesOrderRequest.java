package com.bbd.sales.adapter.in.web.dto;

import com.bbd.sales.domain.SalesOrderPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** POST 생성 요청 body. */
public record CreateSalesOrderRequest(
        @NotBlank String fromWarehouseCode,
        @NotNull SalesOrderPriority priority,
        String note,
        @NotEmpty List<@Valid SalesOrderLineRequest> lines
) {
}
