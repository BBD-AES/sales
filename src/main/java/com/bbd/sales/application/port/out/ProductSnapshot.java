package com.bbd.sales.application.port.out;

import java.math.BigDecimal;

/** 상품 마스터 조회 결과(주문 시점 박제용 스냅샷 원본). */
public record ProductSnapshot(
        String sku,
        String name,
        BigDecimal unitPrice,
        SourcingType sourcingType   // 부족분 분기용(BUY=구매, MAKE=생산)
) {
}
