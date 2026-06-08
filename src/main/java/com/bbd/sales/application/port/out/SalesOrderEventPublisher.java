package com.bbd.sales.application.port.out;

/**
 * 아웃바운드 포트: 상태 변경 이벤트 발행. 코어의 계약("이벤트가 일어났다"만 표현)으로 전송수단을 코어에서 격리한다.
 * 현재 기본 구현은 트랜잭셔널 아웃박스(OutboxSalesOrderEventPublisher, @Primary)이며, 로깅 스텁은 폴백으로 남아 있다.
 * 발행 메서드는 라이프사이클 상태에 맞춘다(approved -> fulfilling).
 */
public interface SalesOrderEventPublisher {
    void publishRequested(String soNumber);
    void publishUpdated(String soNumber);
    void publishSubmitted(String soNumber);
    void publishCanceled(String soNumber);
    void publishFulfilling(String soNumber);
    void publishBackordered(String soNumber);
    void publishRejected(String soNumber);
    void publishReceived(String soNumber);
}
