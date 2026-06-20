package com.bbd.sales.application.port.out;

import java.util.List;

/**
 * 아웃바운드 포트: 재고 컨텍스트 위임.
 * 재고 '판단/정합성'은 Inventory 가 소유한다. 여기선 "수령 확정됐으니 이동시켜줘"만 부탁한다.
 * (Caffeine 캐시로 재고를 판단하지 않는다는 기존 원칙 유지)
 */
public interface InventoryPort {
    // 사람이 창고 고르기 위한 현황 조회
    List<WarehouseStock> availability(String sku);
    // 사람이 고른 '한 창고'에서 한 번 예약 → 실제 잡힌 양
    ReservationResult reserveFromWarehouse(String requestId, String soNumber, String sku, String warehouseCode, int quantity);
    // receive 시 출고(차감)
    void transferForSalesOrderReceive(String soNumber, String destinationWarehouseCode, String issuerId, List<StockTransferLine> lines);
    // cancel/reject 시 예약 해제
    void release(String soNumber);
}