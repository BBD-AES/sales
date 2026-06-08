package com.bbd.sales.application.port.out;

/**
 * 헥사고날에서 필요이유: 코어의 계약. "이벤트가 일어났다"만 표현. 전송수단을 코어에서 격리.
 * 아웃바운드 포트: 상태 변경 이벤트 발행.
 * 현재는 in-process(로깅) 스텁이고, 향후 트랜잭셔널 아웃박스/브로커로 교체 가능(포트 시그니처 유지).
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
