package com.bbd.sales.adapter.in.web.dto;

/** 가용 조회 응답(창고 1개분). 사람이 보고 예약 창고를 고른다. */
public record WarehouseStockResponse(String warehouseCode, String warehouseName, int available) {}
