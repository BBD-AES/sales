package com.bbd.sales.adapter.out.item.dto;


/**
 * item 서비스 GET /api/v1/items/{sku} 응답 중 sales가 쓰는 필드만.
 * (item 실제 응답엔 category/unit/safetyStock 도 있으나 무시됨)
 *
 * sourcingType("BUY"/"MAKE")은 백오더 라우팅 힌트로 SO 라인 스냅샷에 박제하려고 받는다.
 * 미지정(null)이면 sales 는 라인 sourcingType=null 로 두고, 다운스트림(procurement)이 item 마스터로 폴백한다.
 */
public record ItemApiResponse(
        String sku,
        String name,
        int unitPrice, // item은 원화 정수
        boolean active,
        String sourcingType // "BUY"|"MAKE"|null — item 마스터 조달구분(라우팅 힌트)
) {
}
