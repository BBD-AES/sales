package com.bbd.sales.notification;

/**
 * 출고요청 생성(REQUESTED) 자가알림용 in-process 이벤트 — {@link SalesOrderInFulfillmentEvent} 와 대칭.
 * 대상은 '요청 지점'이다(점장이 제출 검토). targetRole 에 요청 지점 창고명(이름축)을 담아
 * inbox 가 caller.warehouseName 으로 스코프 조회한다(전 지점 점장에게 가지 않도록).
 * 발행: OutboxSalesOrderEventPublisher.publishRequested. 수신: BranchNotificationListener(AFTER_COMMIT, best-effort).
 *
 * @param soNumber            생성된 출고요청 번호(REQUESTED)
 * @param branchWarehouseName 요청 지점 창고명(스냅샷) = 알림 대상 버킷
 * @param eventId             알림 행 식별자(발행당 UUID, notification.event_id UNIQUE)
 */
public record SalesOrderRequestedEvent(String soNumber, String branchWarehouseName, String eventId) {
}
