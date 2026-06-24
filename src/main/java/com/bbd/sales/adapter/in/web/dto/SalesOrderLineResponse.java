package com.bbd.sales.adapter.in.web.dto;

import com.bbd.sales.domain.FulfillmentSource;
import com.bbd.sales.domain.SourcingType;

import java.math.BigDecimal;

public record SalesOrderLineResponse(
        int lineNo,
        String sku,
        String nameSnapshot,
        BigDecimal unitPriceSnapshot,
        SourcingType sourcingType, // 조달구분 스냅샷(BUY/MAKE/null)
        int quantity,
        int reservedQuantity,
        FulfillmentSource fulfillmentSource
) {
}
