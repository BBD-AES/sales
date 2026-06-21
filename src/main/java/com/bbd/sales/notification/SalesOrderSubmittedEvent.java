package com.bbd.sales.notification;

/**
 * #65: submit 자가알림용 in-process 이벤트(Spring ApplicationEvent로 발행).
 * sales 내부에서만 발행·소비(타 서비스 미구독 — grep 확인)라 Kafka 대신 in-process 로 전달해 브로커 의존을 제거한다.
 * 발행: OutboxSalesOrderEventPublisher.publishSubmitted(out 포트 임플). 수신: HqNotificationListener(@EventListener, 동기).
 *
 * @param soNumber 제출된 출고요청 번호
 * @param eventId  알림 행 식별자(발행당 UUID). in-process 1회 발행이라 dedup 용이 아니라 알림 행 식별/스키마 유지용.
 */
public record SalesOrderSubmittedEvent(String soNumber, String eventId) {
}
