package com.bbd.sales.adapter.in.web.dto;

import java.util.List;

public record SalesOrderPageResponse<T>(
        List<T> items,
        PaginationResponse pagination
) {
}
