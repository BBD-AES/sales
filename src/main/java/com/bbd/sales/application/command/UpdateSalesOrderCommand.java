package com.bbd.sales.application.command;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.global.security.CurrentUser;

import java.util.List;

/** 수정 유스케이스 입력값. */
public record UpdateSalesOrderCommand(
        String soNumber,
        SalesOrderPriority priority,
        String note,
        List<SalesOrderLineCommand> lines,   // null 이면 라인 변경 없음, 비-null 이면 전체 교체
        CurrentUser currentUser
) {
    public boolean hasLineReplacement() {
        return lines != null;
    }
}
