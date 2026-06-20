package com.bbd.sales.adapter.out.inventory;

import com.bbd.sales.adapter.out.inventory.dto.*;
import com.bbd.sales.application.port.out.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [재고 아웃바운드 어댑터 — 수동 멀티창고 모델, 동기 REST]
 *
 * 역할: 코어가 InventoryPort 로 "부탁한 것"을 inventory REST 호출로 "번역"한다.
 *   코어(SalesOrderService)는 이 클래스를 모르고 InventoryPort(인터페이스)에만 의존한다.
 *
 * 활성화: application.yml `sales.inventory.mode: rest` 일 때만 빈 생성(기본 stub).
 *   inventory 엔드포인트가 실제로 생기기 전엔 stub 유지 → 켜는 순간 404 나는 일 방지.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sales.inventory.mode", havingValue = "rest")
public class InventoryRestAdapter implements InventoryPort {

    /** inventory 로 거는 '전화기'. Spring 이 어노테이션 보고 HTTP 호출코드를 자동 생성. */
    private final InventoryStockHttpService client;

    /**
     * [가용 조회] 사람이 창고를 고르도록 화면에 보여줄 현황. (결정이 아니라 참고용 — 약간 stale 해도 무해)
     * 예: "OIL-FLT-001 → 서울DC 3개 / 부산DC 30개" 를 HQ 에게 보여줌.
     */
    @Override
    public List<WarehouseStock> availability(String sku) {
        // inventory 가용조회는 멀티 sku → List 응답. 단건이므로 sku 1개로 호출하고 '요청 sku 행'을 명시 매칭(index 의존 X).
        List<StockAvailabilityResponse> res = client.availability(List.of(sku));
        return res.stream()
                .filter(r -> sku.equals(r.sku()))   // 규약 위반(다른/추가 sku 행 섞임) 방어 — 엉뚱한 창고셋 표시 방지
                .findFirst()
                .map(r -> r.warehouses().stream()
                        .map(w -> new WarehouseStock(w.warehouseCode(), w.warehouseName(), w.available()))
                        .toList())
                .orElse(List.of());                 // 해당 sku 재고 행 없음
    }

    /**
     * [예약 — 사람이 고른 '한 창고'에서 한 번] ★수동 모델의 핵심. for 문 없음.
     *
     * HQ 가 "이 SKU 를, 이 창고에서, N개" 버튼을 누르면 호출 → inventory 가 '실제 잡힌 양'을 돌려준다.
     * 가용이 모자라면 부분만 잡힘(에러 아님). 부족분은 사람이 보고 '다른 창고에서 또 예약'을 누른다.
     *
     * @param requestId 멱등키(UUID). '이 버튼 클릭 한 번'을 가리키는 표식 — 누른 쪽(프론트)이 만들어 넘김.
     *                  같은 클릭이 타임아웃/더블클릭으로 두 번 날아가도 inventory 가 한 번만 처리하게 함.
     *                  (자세한 건 아래 3번 설명)
     * @return 실제 잡힌 양. 50 요청에 30 잡히면 reserved=30, 부족 20.
     */
    @Override
    public ReservationResult reserveFromWarehouse(
            String requestId, String soNumber, String sku, String warehouseCode, int quantity) {
        ReserveResponse r = client.reserve(                                // 단일 창고 예약 호출(딱 1번)
                new ReserveRequest(requestId, soNumber, sku, warehouseCode, quantity));
        // r.reserved()       = 실제 잡힌 양
        // r.remainingRequested() = 못 잡은 분(>0 이면 사람이 다른 창고로 또 예약)
        return new ReservationResult(sku, r.requested(), r.reserved());
    }

    /**
     * [출고/확정] receive 시 1회. 이 SO 의 예약(RESERVED)분 전부를 실제 재고에서 차감(onHand↓).
     * 바디는 soNumber 만 — "이 주문 예약분 다 빼" (어느 창고에서 뺄지는 inventory 가 예약기록 보고 앎).
     * 멱등: 이미 ISSUED 면 inventory 가 skip.
     */
    @Override
    public void transferForSalesOrderReceive(String soNumber, String destinationWarehouseCode,
                                             String issuerId, List<StockTransferLine> lines) {
        client.issue(new IssueRequest(soNumber));
    }

    /**
     * [해제/보상] cancel·reject 시. 이 SO 예약을 전부 풀어 재고를 가용으로 되돌림(reserved↓).
     * 멱등: 이미 RELEASED 면 no-op.
     */
    @Override
    public void release(String soNumber) {
        client.releaseBySoNumber(soNumber);
    }
}