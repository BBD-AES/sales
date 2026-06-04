package com.bbd.sales.adapter.out.inventory;

import com.bbd.sales.application.port.out.InventoryPort;
import com.bbd.sales.application.port.out.ReservationResult;
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
    public List<ReservationResult> reserve(String soNumber, String destinationWarehouseCode, List<StockTransferLine> lines) {
        log.info("[InventoryStub] 재고 예약 so={}, dest={}, lines={} -> 전량 가용 간주", soNumber, destinationWarehouseCode, lines);
        // 스텁: 전량 예약 성공. 실제 어댑터는 원자적 조건부 차감으로 가용분만 예약하고 부족분을 반환.
        return lines.stream()
                .map(l -> new ReservationResult(l.sku(), l.quantity(), l.quantity()))
                .toList();
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
