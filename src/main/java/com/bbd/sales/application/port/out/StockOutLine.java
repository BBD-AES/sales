package com.bbd.sales.application.port.out;

/**
 * 수주(CO) 종료 출고용 라인 — inventory 에 "이 지점 창고에서 이만큼 물리 차감" 요청(#69).
 * warehouseCode = 지점(딜러) 창고. unitPrice = 출고 원장(movement) 기록용 단가.
 */
public record StockOutLine(String sku, int quantity, String warehouseCode, int unitPrice) {
    public StockOutLine {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku는 비어 있을 수 없습니다.");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode는 비어 있을 수 없습니다.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity는 1 이상이어야 합니다: " + quantity);
        }
        if (unitPrice < 0) { // 0 은 허용(무료/프로모 단가 스냅샷), 음수만 차단
            throw new IllegalArgumentException("unitPrice는 음수일 수 없습니다: " + unitPrice);
        }
    }
}
