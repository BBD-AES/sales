package com.bbd.sales.adapter.out.event;

public record SalesOrderEventMessage(
        String eventId,
        String eventType,
        String soNumber,
        String occurredAt,
        // 수령 확정(received) 시 도착 지점(목적지) 창고코드 — inventory가 이 창고에 입고(IN) 적재.
        // 다른 eventType 에선 null. inventory SalesOrderReceived 의 동일 필드명(toWarehouseCode)과 정확히 일치해야 함.
        String toWarehouseCode
) {
}
