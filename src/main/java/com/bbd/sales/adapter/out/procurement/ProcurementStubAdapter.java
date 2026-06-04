package com.bbd.sales.adapter.out.procurement;

import com.bbd.sales.application.port.out.ProcurementPort;
import com.bbd.sales.application.port.out.StockTransferLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 조달(PO) 아웃바운드 어댑터(임시 스텁: 로그만).
 * 가상 vendor 가정 — 발주하면 무조건 입고된다고 본다(거절/리드타임 없음).
 * TODO: 실제 Procurement(구매요청 PR -> PO -> 입고) 연동으로 교체. 수량 산정도 그쪽 책임.
 */
@Slf4j
@Component
public class ProcurementStubAdapter implements ProcurementPort {

    @Override
    public void requestPurchase(String soNumber, String destinationWarehouseCode, List<StockTransferLine> lines) {
        log.info("[ProcurementStub] 구매요청(PR) 접수 so={}, dest={}, lines={} -> procurement가 PO 작성/입고(가상)",
                soNumber, destinationWarehouseCode, lines);
    }
}
