package com.bbd.sales.adapter.in.web.dto;

public record PaginationResponse(
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
