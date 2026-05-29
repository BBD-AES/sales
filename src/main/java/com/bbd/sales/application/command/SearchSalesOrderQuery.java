package com.bbd.sales.application.command;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;
import com.bbd.sales.global.security.CurrentUser;

import java.time.LocalDate;

/**
 * 목록 조회 입력값. (예전 11개짜리 파라미터 나열을 한 객체로 묶음)
 * currentUser 를 포함해서, 서비스가 "지점 사용자는 본인 창고만" 같은 스코프를 적용한다.
 */
public record SearchSalesOrderQuery(
        SalesOrderStatus status,
        SalesOrderPriority priority,
        String fromWarehouseCode,
        String toWarehouseCode,
        String requestedBy,
        LocalDate startDate,
        LocalDate endDate,
        int page,
        int size,
        CurrentUser currentUser
) {
}
