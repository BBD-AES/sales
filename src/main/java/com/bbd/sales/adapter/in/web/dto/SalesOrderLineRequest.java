package com.bbd.sales.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** 생성/수정 요청의 라인 입력. */
public record SalesOrderLineRequest(
        @NotBlank String sku,
        @Min(1) int quantity
) {
}
