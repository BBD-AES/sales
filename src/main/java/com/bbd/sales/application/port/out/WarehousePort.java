package com.bbd.sales.application.port.out;

/**
 * 아웃바운드 포트: 창고 마스터(inventory 서비스) 조회.
 * item과 다른 서비스이므로 포트를 분리함. 창고 코드 -> 표시명 스냅샷
 */
public interface WarehousePort {
    /** 창고 코드 -> 표시명. */
    String warehouseName(String warehouseCode);
}
