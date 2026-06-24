package com.bbd.sales.notification;

/**
 * IN_FULFILLMENT(HQ 승인·전량 예약 확정) 자가알림용 in-process 이벤트.
 * submit/backordered 알림과 대칭 구조지만 대상이 HQ 가 아니라 '도착 지점'이다 — targetRole 에 지점 창고명(이름축)을 담는다.
 * (테넌시가 코드축을 안 주므로 sales 인가/스코핑과 동일하게 이름축으로 일관: inbox 가 caller.warehouseName 으로 조회.)
 * 발행: OutboxSalesOrderEventPublisher.publishInFulfillment. 수신: BranchNotificationListener(AFTER_COMMIT, best-effort).
 *
 * @param soNumber            IN_FULFILLMENT 로 전환된 출고요청 번호
 * @param branchWarehouseName 도착 지점 창고명(스냅샷) = 알림 대상 버킷
 * @param eventId             알림 행 식별자(발행당 UUID, notification.event_id UNIQUE)
 */
public record SalesOrderInFulfillmentEvent(String soNumber, String branchWarehouseName, String eventId) {
}
