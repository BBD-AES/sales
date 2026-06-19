package com.bbd.sales.application.command;

/**
 * 라인 예약 입력(수동 멀티창고 모델).
 * 사람(HQ)이 고른 '한 창고'에서 quantity 만큼 예약. 부족하면 다른 창고로 또 호출.
 * requestId = '예약' 클릭 1회당 멱등키(UUID) — 프론트가 클릭 시 생성해 보냄(재시도/더블클릭 1회화).
 */
public record ReserveLineCommand(
        String soNumber,
        String sku,
        String warehouseCode,
        int quantity,
        String requestId
) {}
