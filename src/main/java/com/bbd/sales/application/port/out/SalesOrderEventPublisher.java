package com.bbd.sales.application.port.out;

/**
 * 아웃바운드 포트: 상태 변경 이벤트 발행. 코어의 계약("이벤트가 일어났다"만 표현)으로 전송수단을 코어에서 격리한다.
 * 구현은 트랜잭셔널 아웃박스(OutboxSalesOrderEventPublisher).
 */
public interface SalesOrderEventPublisher {
    void publishSubmitted(String soNumber);

    /** 승인 결과 부족분 발생(SUBMITTED→BACKORDERED) → HQ 자가알림(in-process). 비핵심 read-model, best-effort. */
    void publishBackordered(String soNumber);

    /** 승인 결과 전량 예약(SUBMITTED→IN_FULFILLMENT) → 도착 지점 자가알림(in-process). 비핵심 read-model, best-effort. */
    void publishInFulfillment(String soNumber, String branchWarehouseName);

    /**
     * 수령 확정(receive) → 토픽 sales.order.received. inventory가 구독해 해당 soNumber의 예약분을 출고(issue)하고,
     * 도착 지점(toWarehouseCode)에 입고(IN) 적재한다. toWarehouseCode 가 출고 후 목적지 재고 증가의 유일한 단서다.
     */
    void publishReceived(String soNumber, String toWarehouseCode);
}
