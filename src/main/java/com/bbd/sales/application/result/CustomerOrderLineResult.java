package com.bbd.sales.application.result;

import java.math.BigDecimal;

public record CustomerOrderLineResult(
        int lineNo, String sku, String nameSnapshot, BigDecimal unitPriceSnapshot, int quantity
) {
}
