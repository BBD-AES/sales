package com.bbd.sales.adapter.in.web.dto;

import java.util.List;
import java.util.Map;

/** 대시보드 집계 응답(#74). byStatus 키는 상태명(String). */
public record SalesOrderStatsResponse(
        Map<String, Long> byStatus,
        Backorder backorder
) {
    public record Backorder(long count, double avgWaitDays, long maxWaitDays, List<TopSku> topSkus) {
    }

    public record TopSku(String sku, String name, long lineCount, long totalQuantity) {
    }
}
