package com.bbd.sales.adapter.out.event;

/**
 * 출고요청 상태변경 in-process 도메인 이벤트.
 * SalesOrderEventPublisher(out 포트) 구현이 발행하고, AFTER_COMMIT 리스너가 비핵심 부수효과를 처리한다.
 * application/도메인은 이 타입을 모른다(어댑터 내부 표현). 브로커 전환 시 여기만 교체.
 */
public sealed interface SalesOrderEvent {

    String soNumber();

    record Requested(String soNumber) implements SalesOrderEvent {}
    record Updated(String soNumber) implements SalesOrderEvent {}
    record Submitted(String soNumber) implements SalesOrderEvent {}
    record Canceled(String soNumber) implements SalesOrderEvent {}
    record Fulfilling(String soNumber) implements SalesOrderEvent {}
    record Backordered(String soNumber) implements SalesOrderEvent {}
    record Rejected(String soNumber) implements SalesOrderEvent {}
    record Received(String soNumber) implements SalesOrderEvent {}
}
