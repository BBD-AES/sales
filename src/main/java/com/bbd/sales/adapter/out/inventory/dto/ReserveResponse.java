package com.bbd.sales.adapter.out.inventory.dto;

public record ReserveResponse(
        String reservationId,
        String soNumber,
        String sku,
        String warehouseCode,
        int requested,
        int reserved, // 실제 잡힌 양 (reserved <= requested)
        int remainingRequested, // 못 잡은 분(>0이면 다른 창고로 재시도)
        int availableAfter
) {
}
