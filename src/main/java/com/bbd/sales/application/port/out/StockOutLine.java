package com.bbd.sales.application.port.out;

/**
 * 수주(CO) 종료 출고용 라인 — inventory 에 "이 지점 창고에서 이만큼 물리 차감" 요청(#69).
 * warehouseCode = 지점(딜러) 창고. unitPrice = 출고 원장(movement) 기록용 단가.
 */
public record StockOutLine(String sku, int quantity, String warehouseCode, int unitPrice) {
}
