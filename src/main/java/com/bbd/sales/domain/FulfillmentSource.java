package com.bbd.sales.domain;

/**
 * 출고요청 라인의 충족 소스.
 *  - STOCK      : 재고에서 전량 확보(=출고)
 *  - PRODUCTION : 부족분을 생산으로 소싱
 *  - PURCHASE   : 부족분을 구매(PR)로 소싱
 * 라인이 전량 확보되면 STOCK, 부족분이 남아 있으면 그 부족분의 소싱 경로를 가리킨다.
 */
public enum FulfillmentSource {
    STOCK,
    PRODUCTION,
    PURCHASE
}
