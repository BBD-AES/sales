package com.bbd.sales.adapter.out.inventory.dto;

import java.util.List;

/**
 * inventory outbound(지점재고 차감) REST 요청(#69). inventory 의 StockOutRequested 와 라인 구조 정합.
 * referenceNumber = coNumber(멱등/원장 참조). 부족 시 inventory 가 409(INSUFFICIENT_STOCK) 응답 → 종료 차단.
 */
public record StockOutboundRequest(String referenceNumber, List<Line> lines) {
    public record Line(String sku, int quantity, String warehouseCode, int unitPrice) {
    }
}
