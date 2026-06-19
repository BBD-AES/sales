package com.bbd.sales.application.port.out;

/**
 * 아웃바운드 포트: 상태 변경 이벤트 발행. 코어의 계약("이벤트가 일어났다"만 표현)으로 전송수단을 코어에서 격리한다.
 * 구현은 트랜잭셔널 아웃박스(OutboxSalesOrderEventPublisher).
 */
public interface SalesOrderEventPublisher {
    void publishSubmitted(String soNumber);
}
