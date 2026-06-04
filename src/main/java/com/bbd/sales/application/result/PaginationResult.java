package com.bbd.sales.application.result;

/** 페이지 메타. application 자체 타입(웹 DTO 의존 제거). */
public record PaginationResult(
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
