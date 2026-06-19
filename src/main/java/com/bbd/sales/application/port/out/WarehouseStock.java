package com.bbd.sales.application.port.out;

/**
 * 가용 조회 결과(창고 1개분). 사람이 창고를 고를 때 화면에 보여줄 현황.
 * 포트(application)의 반환 타입이므로 application.port.out 에 둔다(어댑터에 두면 application→adapter 역의존).
 */
public record WarehouseStock(String warehouseCode, String warehouseName, int available) {}
