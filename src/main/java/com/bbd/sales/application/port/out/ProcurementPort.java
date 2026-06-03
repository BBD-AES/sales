package com.bbd.sales.application.port.out;

import java.util.List;

/**
 * 아웃바운드 포트: 조달(PO) 위임.
 * 전사 재고가 부족(BACKORDERED)할 때 vendor 발주를 요청한다.
 * 수량 산정(안전재고/MOQ/목표재고)은 Procurement 의 책임 — sales 는 부족 사실만 전달한다.
 * 현재는 가상 vendor(거절/리드타임 없음, 무조건 입고) 가정. 추후 실제 Procurement 컨텍스트로 교체.
 */
public interface ProcurementPort {

    /** 재고 부족분에 대한 구매발주(PO) 발행. */
    void raisePurchaseOrder(String soNumber, String destinationWarehouseCode, List<StockTransferLine> lines);
}
