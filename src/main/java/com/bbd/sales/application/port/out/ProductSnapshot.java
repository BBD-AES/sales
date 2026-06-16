package com.bbd.sales.application.port.out;

import java.math.BigDecimal;

/** 상품 마스터 조회 결과(주문 시점 박제용 스냅샷 원본). */
public record ProductSnapshot(
        String sku,
        String name,
        BigDecimal unitPrice,
        boolean active // item 활성 여부 - 주문 시점 검증용(라인에 저장하지 않음)
) {
    /** active 미지정 시 활성으로 간주 - 기존 호출부/스텁/테스트 그대로 컴파일되도록 */
    public ProductSnapshot(String sku, String name, BigDecimal unitPrice) {
        this(sku, name, unitPrice, true);
    }
}
