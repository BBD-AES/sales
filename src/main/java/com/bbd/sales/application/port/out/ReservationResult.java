package com.bbd.sales.application.port.out;

/**
 * 예약 결과(라인별). Inventory 가 가용분만 원자적으로 예약하고 부족분을 보고한다.
 * reserved = 실제 예약(가용)분, requested = 요청량. shortfall = requested - reserved.
 */
public record ReservationResult(String sku, int requested, int reserved, String sourceWarehouseCode) {

    /** 출발지 미지정(=null) 호환 생성자. 테스트/단순 경로용. */
    public ReservationResult(String sku, int requested, int reserved) {
        this(sku, requested, reserved, null);
    }

    public int shortfall() {
        return Math.max(0, requested - reserved);
    }

    public boolean fullyReserved() {
        return reserved >= requested;
    }
}
