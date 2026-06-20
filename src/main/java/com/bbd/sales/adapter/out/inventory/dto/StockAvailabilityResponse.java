package com.bbd.sales.adapter.out.inventory.dto;

import java.util.List;

public record StockAvailabilityResponse(
        String sku,
        List<Warehouse> warehouses
) {
    public record Warehouse(
            String warehouseCode,
            String warehouseName,
            int available   // sales는 예약 결정 기준인 available만 사용. inventory가 보내는 onHand/reserved는 무시(fail-on-unknown=false).
    ) {}
}
