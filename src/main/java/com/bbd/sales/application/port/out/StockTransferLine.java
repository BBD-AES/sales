package com.bbd.sales.application.port.out;

/** Inventory 컨텍스트로 넘길 SKU/수량 라인(포트 계약 전용 DTO). */
public record StockTransferLine(
        String sku,
        int quantity
) {
}
