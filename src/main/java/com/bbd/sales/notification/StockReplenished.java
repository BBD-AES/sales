package com.bbd.sales.notification;

import java.util.List;

/**
 * 인바운드 계약 이벤트 - inventory 발행(topic: inventory.stock-replenished), sales 구독(group: sales-backorder).
 * inventory가 '실제 재고 증가 후' 보내는 보충 완료 통지(procurement 요청이 아니라 실제 입고 반영 후 - 경합 회피).
 * soNumber 있으면 그 SO 백오더 충족 트리거
 */
public record StockReplenished(
        String eventId, // UUID - 멱등 키
        String source, // "inventory"
        String eventType, // "STOCK_REPLENISHED"
        String occurredAt,
        String soNumber, // 연관된 출고요청 번호. 없으면 SO와 무관한 입고임.
        List<Line> lines
) {
    public record Line(String sku, int quantity, String warehouseCode) { }
}
