package com.bbd.sales.adapter.out.procurement;

import java.util.List;

/**
 * 계약 이벤트 DTO — sales 발행, procurement 구독 (토픽: {@code sales.purchase-requested}).
 * 조직 이벤트 계약서(docs/eda-event-spec.md §3-4)의 envelope 4필드 + 페이로드.
 * 구독 레포(procurement)에 이 record를 그대로 복붙한다(JSON 운반이라 공유 jar 불필요).
 */
public record PurchaseRequested(
        String eventId,        // 이벤트 1건당 UUID — 컨슈머 멱등 키(at-least-once 대비)
        String source,         // "sales" 고정
        String eventType,      // "PURCHASE_REQUESTED" 고정
        String occurredAt,     // ISO-8601 UTC Instant (예: 2026-06-15T02:20:00Z)
        String soNumber,       // 연관 수주번호(= Kafka 메시지 key). procurement는 PO.soId로 보관 → 입고 시 백오더 트리거로 회신
        String warehouseCode,  // 입고 목적지 창고(수주 도착창고)
        List<Line> lines
) {
    public record Line(
            String sku,
            int quantity       // 부족 수량(구매 요청 수량). 단가 없음 — 협상가는 procurement가 PO에서 결정
    ) {}
}
