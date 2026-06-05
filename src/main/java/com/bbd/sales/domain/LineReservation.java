package com.bbd.sales.domain;

/**
 * confirm/refulfill 입력값: 라인(sku)별 '이번 라운드 확보 수량(reserved)'과 '부족분 소스(source)'.
 * 서비스가 InventoryPort 예약 결과 + 품목 조달유형으로 구성해 도메인에 넘긴다.
 */
public record LineReservation(String sku, int reserved, FulfillmentSource source) {
}
