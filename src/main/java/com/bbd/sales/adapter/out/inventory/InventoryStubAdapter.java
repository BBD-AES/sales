package com.bbd.sales.adapter.out.inventory;

import com.bbd.sales.application.port.out.InventoryPort;
import com.bbd.sales.application.port.out.ReservationResult;
import com.bbd.sales.application.port.out.StockTransferLine;
import com.bbd.sales.application.port.out.WarehouseStock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 재고 아웃바운드 어댑터(임시 스텁).
 * TODO: Inventory 컨텍스트 REST/메시지 호출로 교체. 포트 시그니처는 그대로 두면 됨.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sales.inventory.mode", havingValue = "stub", matchIfMissing = true)
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
    public List<WarehouseStock> availability(String sku) {
        // 데모: 등록 SKU는 그 수량, 미등록은 넉넉히 가용. 본사 중앙창고 1곳만 노출.
        int available = DEMO_AVAILABLE.getOrDefault(sku, 999);
        return List.of(new WarehouseStock("WH-HQ-001", "본사 중앙창고", available));
    }

    @Override
    public ReservationResult reserveFromWarehouse(String requestId, String soNumber,
                                                  String sku, String warehouseCode, int quantity) {
        // 데모: 가용분만 잡힘(부분 정상). 미등록 SKU는 요청 전량 가용. 실연동 시 inventory가 원자적으로 판정.
        int available = DEMO_AVAILABLE.getOrDefault(sku, quantity);
        int reserved = Math.min(quantity, available);
        log.info("[InventoryStub] 예약(데모) req={}, so={}, sku={}, wh={}, 요청={}, 잡힘={}",
                requestId, soNumber, sku, warehouseCode, quantity, reserved);
        return new ReservationResult(sku, quantity, reserved, warehouseCode);
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

    @Override
    public void release(String soNumber) {

    }
}
