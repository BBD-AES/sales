package com.bbd.sales.application.port.out;

import java.util.List;

/**
 * 아웃바운드 포트: 재고 컨텍스트 위임.
 * 재고 '판단/정합성'은 Inventory 가 소유한다. 여기선 "수령 확정됐으니 이동시켜줘"만 부탁한다.
 * (Caffeine 캐시로 재고를 판단하지 않는다는 기존 원칙 유지)
 */
public interface InventoryPort {

    /**
     * 수령 확정 시 실재고 이동.
     * @param soNumber               출고 요청 번호
     * @param sourceWarehouseCode    출발 창고(=HQ, toWarehouseCode)
     * @param destinationWarehouseCode 도착 창고(=지점, fromWarehouseCode)
     * @param issuerId               처리자 사번
     * @param lines                  이동 라인
     */
    void transferForSalesOrderReceive(
            String soNumber,
            String sourceWarehouseCode,
            String destinationWarehouseCode,
            String issuerId,
            List<StockTransferLine> lines
    );
}
