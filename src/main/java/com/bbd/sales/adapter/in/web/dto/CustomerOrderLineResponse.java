package com.bbd.sales.adapter.in.web.dto;

import java.math.BigDecimal;

public record CustomerOrderLineResponse(
        int lineNo, String sku, String nameSnapshot, BigDecimal unitPriceSnapshot, int quantity
) {
}
