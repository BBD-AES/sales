package com.bbd.sales.adapter.out.inventory;

import com.bbd.sales.application.port.out.InventoryPort;
import com.bbd.sales.application.port.out.StockTransferLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 재고 아웃바운드 어댑터(임시 스텁).
 * TODO: Inventory 컨텍스트 REST/메시지 호출로 교체. 포트 시그니처는 그대로 두면 됨.
 */
@Slf4j
@Component
public class InventoryStubAdapter implements InventoryPort {

    @Override
    public boolean reserve(String soNumber, String destinationWarehouseCode, List<StockTransferLine> lines) {
        log.info("[InventoryStub] 재고 예약 so={}, dest={}, lines={} -> 성공 간주", soNumber, destinationWarehouseCode, lines);
        return true;   // 스텁: 항상 가용. 실제 어댑터는 원자적 조건부 차감 결과 반환(부족 시 false -> BACKORDERED).
    }

    @Override
    public void transferForSalesOrderReceive(String soNumber,
                                             String destinationWarehouseCode,
                                             String issuerId,
                                             List<StockTransferLine> lines) {
        log.info("[InventoryStub] 재고 이동 요청 so={}, -> {}, issuer={}, lines={}",
                soNumber, destinationWarehouseCode, issuerId, lines);
        // 실제 구현 전까지는 성공으로 간주(no-op). source 는 추후 할당 기록으로 해석.
    }
}
