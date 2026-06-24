package com.bbd.sales.application.port.out;

import com.bbd.sales.domain.SourcingType;

import java.math.BigDecimal;

/** 상품 마스터 조회 결과(주문 시점 박제용 스냅샷 원본). */
public record ProductSnapshot(
        String sku,
        String name,
        BigDecimal unitPrice,
        boolean active,         // item 활성 여부 - 주문 시점 검증용(라인에 저장하지 않음)
        SourcingType sourcingType // 조달구분(BUY/MAKE) - 라인에 스냅샷 저장. item 미제공 시 null(다운스트림 폴백)
) {
    /** sourcingType 미지정 시 null - item 이 조달구분을 안 주는 경우/기존 호출부 호환. */
    public ProductSnapshot(String sku, String name, BigDecimal unitPrice, boolean active) {
        this(sku, name, unitPrice, active, null);
    }

    /** active 미지정 시 활성으로 간주 - 기존 호출부/스텁/테스트 그대로 컴파일되도록 */
    public ProductSnapshot(String sku, String name, BigDecimal unitPrice) {
        this(sku, name, unitPrice, true, null);
    }
}
