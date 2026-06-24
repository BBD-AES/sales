package com.bbd.sales.application.result;

import com.bbd.sales.domain.FulfillmentSource;
import com.bbd.sales.domain.SourcingType;

import java.math.BigDecimal;

public record SalesOrderLineResult(
        int lineNo,
        String sku,
        String nameSnapshot,
        BigDecimal unitPriceSnapshot,
        SourcingType sourcingType, // 조달구분 스냅샷(BUY/MAKE/null) — 요청 대기 라우팅 표시용
        int quantity,
        int reservedQuantity,
        FulfillmentSource fulfillmentSource
) {
}
