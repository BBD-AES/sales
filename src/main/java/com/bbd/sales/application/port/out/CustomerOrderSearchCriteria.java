package com.bbd.sales.application.port.out;

import com.bbd.sales.domain.CustomerOrderStatus;

import java.time.LocalDateTime;

/**
 * 영속성 검색 필터. 권한/스코프가 이미 반영된 '순수 필터'다.
 * (지점 사용자 본인지점 한정 스코핑은 서비스가 적용한 뒤 dealerName 으로 박아 넘긴다 — SalesOrderSearchCriteria 와 동일 패턴)
 */
public record CustomerOrderSearchCriteria(
        CustomerOrderStatus status,
        String dealerWarehouseCode, // HQ 선택 필터(코드축)
        String dealerName,          // 지점 본인지점 스코핑(이름축)
        String customerName,
        String requestedBy, LocalDateTime from, LocalDateTime to
) {
}
