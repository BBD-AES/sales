package com.bbd.sales.adapter.in.web.dto;

import com.bbd.sales.domain.FulfillmentSource;

import java.math.BigDecimal;

public record SalesOrderLineResponse(
        int lineNo,
        String sku,
        String nameSnapshot,
        BigDecimal unitPriceSnapshot,
        int quantity,
        int reservedQuantity,
        FulfillmentSource fulfillmentSource,
        String fromWarehouseCode
) {
}
