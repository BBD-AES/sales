package com.bbd.sales.application.command;

/**
 * 라인 예약 입력(수동 멀티창고 모델).
 * 사람(HQ)이 고른 '한 창고'에서 quantity 만큼 예약. 부족하면 다른 창고로 또 호출.
 * idempotencyKey = 공통 멱등 토큰(Idempotency-Key). 컨트롤러가 인바운드 헤더를 해석해 채우며,
 *   inventory 예약 dedup(영속 UNIQUE)의 키로 그대로 전파된다(재시도/더블클릭 1회화).
 */
public record ReserveLineCommand(
        String soNumber,
        String sku,
        String warehouseCode,
        int quantity,
        String idempotencyKey
) {}
