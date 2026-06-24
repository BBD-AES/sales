package com.bbd.sales.adapter.out.inventory.dto;

public record ReserveRequest(
        String requestId, // = 공통 멱등 토큰(Idempotency-Key) 과 동일 값. 헤더에도 같이 실어 보냄(레거시 inventory 폴백용)
        String soNumber,
        String sku,
        String warehouseCode, // 이 호출이 예약할 단일 창고
        int quantity
) {
}
