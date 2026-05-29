package com.bbd.sales.application.port.out;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;

import java.time.LocalDateTime;

/**
 * 영속성 검색 필터. 권한/스코프가 이미 반영된 '순수 필터'다.
 * (지점 사용자 본인창고 한정 같은 스코핑은 서비스가 적용한 뒤 여기에 fromWarehouseCode 로 박아 넘긴다)
 */
public record SalesOrderSearchCriteria(
        SalesOrderStatus status,
        SalesOrderPriority priority,
        String fromWarehouseCode,
        String toWarehouseCode,
        String requestedBy,
        LocalDateTime from,
        LocalDateTime to
) {
}
