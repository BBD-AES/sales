package com.bbd.sales.adapter.out.item.dto;

import com.bbd.sales.application.port.out.SourcingType;

/**
 * item 서비스 GET /api/v1/items/{sku} 응답 중 sales가 쓰는 필드만.
 * (item 실제 응답엔 category/unit/safetyStock도 있으나 무시됨)
 */
public record ItemApiResponse(
        String sku,
        String name,
        int unitPrice, // item은 원화 정수
        SourcingType sourcingType, // "MAKE"/"BUY" 문자열 -> sales enum 그대로 역직렬화
        boolean active
) {
}
