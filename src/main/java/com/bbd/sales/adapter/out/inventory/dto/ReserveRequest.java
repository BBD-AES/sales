package com.bbd.sales.adapter.out.inventory.dto;

public record ReserveRequest(
        String requestId, // UUID 멱등키(sales 생성)
        String soNumber,
        String sku,
        String warehouseCode, // 이 호출이 예약할 단일 창고
        int quantity
) {
}
