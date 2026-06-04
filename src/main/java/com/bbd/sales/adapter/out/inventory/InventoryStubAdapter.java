package com.bbd.sales.adapter.out.inventory;

import com.bbd.sales.application.port.out.InventoryPort;
import com.bbd.sales.application.port.out.ReservationResult;
import com.bbd.sales.application.port.out.StockTransferLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 재고 아웃바운드 어댑터(임시 스텁).
 * TODO: Inventory 컨텍스트 REST/메시지 호출로 교체. 포트 시그니처는 그대로 두면 됨.
 */
@Slf4j
@Component
public class InventoryStubAdapter implements InventoryPort {

    /**
     * [데모용] 가용재고. 여기 없는 SKU 는 전량 가용으로 간주(→ 출고).
     * 실 Inventory 연동 시 이 맵은 사라지고, 원자적 조건부 차감 결과로 대체된다.
     */
    private static final Map<String, Integer> DEMO_AVAILABLE = Map.of(
            "RLY-12V-30A-01", 0,   // 무재고 -> BUY 분기(구매요청) 시연
            "CLT-DSK-MED-01", 1    // 부족   -> MAKE 분기(생산요청) 시연 (CatalogStub 에서 MAKE)
    );

    @Override
    public List<ReservationResult> reserve(String soNumber, String destinationWarehouseCode, List<StockTransferLine> lines) {
        List<ReservationResult> results = lines.stream()
                .map(l -> {
                    int available = DEMO_AVAILABLE.getOrDefault(l.sku(), l.quantity()); // 미등록 SKU=전량 가용
                    return new ReservationResult(l.sku(), l.quantity(), Math.min(l.quantity(), available));
                })
                .toList();
        log.info("[InventoryStub] 재고 예약(데모) so={}, dest={}, 결과={}", soNumber, destinationWarehouseCode, results);
        return results;
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
