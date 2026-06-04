package com.bbd.sales.adapter.out.production;

import com.bbd.sales.application.port.out.ProductionPort;
import com.bbd.sales.application.port.out.StockTransferLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 생산 아웃바운드 어댑터(임시 스텁: 로그만).
 * 가상 생산 가정 — 요청하면 납기일에 입고된다고 본다(BOM/MRP/작업지시/거절 없음).
 * TODO: 실제 Production(소요량 계산 -> 작업지시 -> 완제품 입고) 연동으로 교체.
 */
@Slf4j
@Component
public class ProductionStubAdapter implements ProductionPort {

    @Override
    public void requestProduction(String soNumber, String destinationWarehouseCode, List<StockTransferLine> lines) {
        log.info("[ProductionStub] 생산요청 접수 so={}, dest={}, lines={} -> 가상 생산/납기일 입고 가정",
                soNumber, destinationWarehouseCode, lines);
    }
}
