package com.bbd.sales.adapter.out.event;

public record SalesOrderEventMessage(
        String eventId,
        String eventType,
        String soNumber,
        String occurredAt
) {
}
