package com.bbd.sales.application.port.out;

import java.util.List;

/**
 * 아웃바운드 포트: 조달(구매요청, PR) 위임.
 * 전사 재고가 부족할 때 sales HQ 가 procurement HQ 에 '구매요청(PR)'을 보낸다.
 * 실제 PO 작성·발주·수량 산정(안전재고/MOQ/목표재고)은 Procurement 의 책임 — sales 는 부족 사실만 전달.
 * 현재는 가상 vendor(거절/리드타임 없음, 무조건 입고) 가정. 추후 실제 Procurement 컨텍스트로 교체.
 */
public interface ProcurementPort {

    /** 재고 부족분에 대한 구매요청(PR). sales HQ -> procurement HQ. PO 작성은 procurement 책임. */
    void requestPurchase(String soNumber, String destinationWarehouseCode, List<ShortfallLine> lines);
}
