package com.bbd.sales.application.result;

import com.bbd.sales.domain.SalesOrderStatus;

import java.util.List;
import java.util.Map;

/**
 * 대시보드 집계 결과. 상태별 카운트 + 백오더 분석.
 * 지점 사용자는 본인 창고로 스코프된 값(서비스에서 처리).
 */
public record SalesOrderStatsResult(
        Map<SalesOrderStatus, Long> byStatus,
        BackorderStats backorder
) {
    /** 백오더(BACKORDERED) 분석. waitDays = 요청 후 경과(별도 백오더 타임스탬프 없어 근사). */
    public record BackorderStats(
            long count,
            double avgWaitDays,
            long maxWaitDays,
            List<TopSku> topSkus
    ) {
    }

    /** 백오더 유발 상위 품목(라인 빈도순). */
    public record TopSku(String sku, String name, long lineCount, long totalQuantity) {
    }
}
