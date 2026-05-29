package com.bbd.sales.application.port.out;

/**
 * 아웃바운드 포트: 상태 변경 이벤트 발행(Kafka/Outbox 등).
 * 파라미터명 오타(soNUmber)를 정리했다.
 */
public interface SalesOrderEventPublisher {
    void publishRequested(String soNumber);
    void publishUpdated(String soNumber);
    void publishCanceled(String soNumber);
    void publishApproved(String soNumber);
    void publishRejected(String soNumber);
    void publishReceived(String soNumber);
}
