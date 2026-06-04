package com.bbd.sales.application.port.out;

import java.util.List;

/**
 * 아웃바운드 포트: 생산(부품 조립/제조) 위임.
 * 부족분이 '생산품(MAKE)'일 때 sales 가 생산요청을 보낸다.
 * 실제 생산계획(BOM 전개/MRP/작업지시/스케줄링)은 생산 모듈 책임 — sales 는 부족 사실만 전달.
 * 현재는 가상 생산(거절/스케줄링 없음, 납기일 입고 가정) 스텁. 추후 실제 Production 컨텍스트로 교체.
 */
public interface ProductionPort {

    /** 부족분 생산요청. sales -> production. 수량 산정/소요량 계산은 생산 모듈 책임. */
    void requestProduction(String soNumber, String destinationWarehouseCode, List<StockTransferLine> lines);
}
