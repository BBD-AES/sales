package com.bbd.sales.adapter.out.warehouse.dto;

/**
 * inventory 창고 조회 응답(필요 필드만).
 * inventory WarehouseResponse(code, name, type, address, active) 중 sales는 name만 사용.
 * 나머지 필드는 자동 무시.
 */
public record WarehouseResponse(String code, String name) {
}
