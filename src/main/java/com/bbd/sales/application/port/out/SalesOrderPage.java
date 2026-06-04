package com.bbd.sales.application.port.out;

import com.bbd.sales.domain.SalesOrder;

import java.util.List;

/** 영속성 검색 결과(도메인 객체 목록 + 전체 건수). */
public record SalesOrderPage(
        List<SalesOrder> content,
        long totalElements,
        int page,
        int size
) {
    public int totalPages() {
        if (size <= 0) return 0;
        return (int) Math.ceil((double) totalElements / size);
    }
}
