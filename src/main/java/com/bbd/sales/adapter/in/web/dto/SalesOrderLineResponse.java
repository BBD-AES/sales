package com.bbd.sales.adapter.in.web.dto;

import java.math.BigDecimal;

public record SalesOrderLineResponse(
        int lineNo,
        String sku,
        String nameSnapshot,
        BigDecimal unitPriceSnapshot,
        int quantity
) {
}
