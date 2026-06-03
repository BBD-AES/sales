package com.bbd.sales.application.port.out;

import java.util.List;

/**
 * 아웃바운드 포트: 재고 컨텍스트 위임.
 * 재고 '판단/정합성'은 Inventory 가 소유한다. 여기선 "수령 확정됐으니 이동시켜줘"만 부탁한다.
 * (Caffeine 캐시로 재고를 판단하지 않는다는 기존 원칙 유지)
 */
public interface InventoryPort {

    /**
     * HQ 승인 시 재고 예약(동기). available 재고를 원자적으로 차감해 오버셀(음수)을 막는다.
     * 출발지(source) 선정/할당은 Inventory 가 담당하며, 예약 기록은 soNumber 로 멱등 관리한다.
     * @return true = 전량 예약 성공(-> IN_FULFILLMENT), false = 재고 부족(-> BACKORDERED -> PO)
     */
    boolean reserve(String soNumber, String destinationWarehouseCode, List<StockTransferLine> lines);

    /**
     * 수령 확정 시 실재고 이동.
     * 출발지(source)는 sales 가 모른다 -> Inventory 가 soNumber 의 할당/예약 기록으로 해석한다.
     * @param soNumber                 출고 요청 번호(할당 기록 키)
     * @param destinationWarehouseCode 도착 창고(=지점, fromWarehouseCode)
     * @param issuerId                 처리자 사번
     * @param lines                    이동 라인
     */
    void transferForSalesOrderReceive(
            String soNumber,
            String destinationWarehouseCode,
            String issuerId,
            List<StockTransferLine> lines
    );
}
