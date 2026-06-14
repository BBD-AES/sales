package com.bbd.sales.application.result;

import com.bbd.sales.domain.FulfillmentSource;

import java.math.BigDecimal;

public record SalesOrderLineResult(
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
