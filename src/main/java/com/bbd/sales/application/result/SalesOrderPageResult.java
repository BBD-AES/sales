package com.bbd.sales.application.result;

import java.util.List;

/** 목록 결과 wrapper. 이전엔 web 의 PaginationResponse 를 참조해 경계가 샜었음 -> PaginationResult 로 교정. */
public record SalesOrderPageResult<T>(
        List<T> items,
        PaginationResult pagination
) {
}
