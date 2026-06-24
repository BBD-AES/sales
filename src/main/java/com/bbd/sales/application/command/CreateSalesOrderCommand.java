package com.bbd.sales.application.command;

import com.bbd.sales.domain.SalesOrderPriority;

import java.util.List;

/**
 * 생성 유스케이스 입력값. 웹 DTO 를 application 용으로 변환한 것.
 * (CreateSalesOrderRequest 같은 어댑터 타입을 application 이 직접 참조하지 않게 하는 경계 역할)
 */
public record CreateSalesOrderCommand(
        String toWarehouseCode,
        SalesOrderPriority priority,
        String note,
        String customerOrderNumber,   // 연계 고객주문(CO) 번호 — 선택(null=미지정).
        List<SalesOrderLineCommand> lines,
        String idempotencyKey   // #71: 클라이언트 Idempotency-Key(헤더). null=멱등 미적용(현행 통과).
) {
    /** customerOrderNumber 없이 생성하는 하위호환 생성자(기존 호출부·테스트 유지). */
    public CreateSalesOrderCommand(String toWarehouseCode, SalesOrderPriority priority, String note,
                                   List<SalesOrderLineCommand> lines, String idempotencyKey) {
        this(toWarehouseCode, priority, note, null, lines, idempotencyKey);
    }
}
