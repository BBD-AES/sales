package com.bbd.sales.adapter.out.inventory.dto;

import java.util.List;

public record StockAvailabilityResponse(
        String sku,
        List<Warehouse> warehouses
) {
    public record Warehouse(
            String warehouseCode,
            String warehouseName,
            int onHand,
            int reserved,
            int available
    ) {}
}
