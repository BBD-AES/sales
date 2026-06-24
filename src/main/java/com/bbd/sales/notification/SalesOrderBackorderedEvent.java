package com.bbd.sales.notification;

/**
 * 백오더(재고 부족) 자가알림용 in-process 이벤트. submit 알림(SalesOrderSubmittedEvent)과 대칭 구조.
 * 발행: OutboxSalesOrderEventPublisher.publishBackordered. 수신: HqNotificationListener(AFTER_COMMIT, best-effort).
 *
 * @param soNumber BACKORDERED 로 전환된 출고요청 번호
 * @param eventId  알림 행 식별자(발행당 UUID, notification.event_id UNIQUE)
 */
public record SalesOrderBackorderedEvent(String soNumber, String eventId) {
}
