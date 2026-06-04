package com.bbd.sales.application.port.out;

/**
 * 품목 조달 유형(item 마스터 속성, CatalogPort 로 조회).
 * 부족분 분기에 사용한다: BUY -> 구매요청(PR, procurement), MAKE -> 생산요청(production).
 */
public enum SourcingType {
    BUY,   // 외부 구매품(vendor 에서 사옴)
    MAKE   // 사내 생산품(부품으로 조립/제조)
}
