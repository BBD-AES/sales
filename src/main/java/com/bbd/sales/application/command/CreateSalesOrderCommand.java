package com.bbd.sales.application.command;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.global.security.CurrentUser;

import java.util.List;

/**
 * 생성 유스케이스 입력값. 웹 DTO 를 application 용으로 변환한 것.
 * (CreateSalesOrderRequest 같은 어댑터 타입을 application 이 직접 참조하지 않게 하는 경계 역할)
 */
public record CreateSalesOrderCommand(
        String fromWarehouseCode,
        SalesOrderPriority priority,
        String note,
        List<SalesOrderLineCommand> lines,
        CurrentUser currentUser
) {
}
