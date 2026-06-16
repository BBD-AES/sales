package com.bbd.sales.domain;

/**
 * 출고요청 라인의 충족 소스.
 *  - STOCK      : 재고에서 전량 확보(=출고)
 *  - BACKORDERED : 부족분 존재 -> procurement가 buy/make 판정/소싱(sales는 미결정)
 */
public enum FulfillmentSource {
    STOCK,
    BACKORDERED;
}
