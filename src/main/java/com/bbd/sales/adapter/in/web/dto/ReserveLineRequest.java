package com.bbd.sales.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 라인 예약 요청. 사람이 고른 한 창고에서 quantity 만큼.
 * 멱등 토큰은 게이트웨이가 강제하는 {@code Idempotency-Key} 헤더를 표준으로 쓴다(공통 멱등 표준).
 * requestId 는 헤더 미전송 레거시 클라이언트 호환용 '선택' 폴백일 뿐 — 신규 클라이언트는 보내지 않아도 된다.
 */
public record ReserveLineRequest(
        @NotBlank String sku,
        @NotBlank String warehouseCode,
        @Positive int quantity,
        String requestId   // 레거시 선택 폴백(헤더 Idempotency-Key 가 표준 키)
) {}
