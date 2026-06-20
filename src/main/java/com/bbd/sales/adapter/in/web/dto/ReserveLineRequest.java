package com.bbd.sales.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 라인 예약 요청. 사람이 고른 한 창고에서 quantity 만큼.
 * requestId = 프론트가 '예약' 클릭마다 생성하는 멱등키(UUID). 재시도/더블클릭이 한 번만 먹게 한다.
 */
public record ReserveLineRequest(
        @NotBlank String sku,
        @NotBlank String warehouseCode,
        @Positive int quantity,
        @NotBlank String requestId
) {}
