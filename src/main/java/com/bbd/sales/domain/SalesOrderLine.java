package com.bbd.sales.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 출고 요청 라인. 도메인 값 객체.
 *
 * nameSnapshot / unitPriceSnapshot 은 "주문 시점의" 상품명·단가를 박제한 값이다.
 * 상품 마스터가 나중에 바뀌어도 과거 주문 금액은 변하면 안 되므로 스냅샷으로 보관한다.
 * 스냅샷 원본을 어디서 가져오는지(상품/재고 컨텍스트)는 도메인이 알 바 아니고,
 * application 서비스가 out 포트(ItemPort)로 조회해 채워 넣는다.
 */
public class SalesOrderLine {

    private final int lineNo;
    private final String sku;
    private final String nameSnapshot;
    private final BigDecimal unitPriceSnapshot;
    private final int quantity;

    // 라인레벨 충족추적: 재고 확보분 + 부족분 소스(confirm 에서 채워짐).
    private int reservedQuantity = 0;
    private FulfillmentSource fulfillmentSource;   // null = 미확정(confirm 전). 이후 STOCK 또는 BACKORDER(부족분 소스)
    private String fromWarehouseCode;              // 출발지(출고창고)=소스. confirm 시 재고 확보분에 기록, 전 null

    public SalesOrderLine(int lineNo, String sku, String nameSnapshot,
                          BigDecimal unitPriceSnapshot, int quantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku 는 필수입니다.");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity 는 1 이상이어야 합니다.");
        }
        this.lineNo = lineNo;
        this.sku = sku;
        this.nameSnapshot = nameSnapshot;
        if (unitPriceSnapshot == null || unitPriceSnapshot.signum() <= 0) {
            throw new IllegalArgumentException("unitPriceSnapshot 는 0보다 커야 합니다." + unitPriceSnapshot);
        }
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.quantity = quantity;
    }

    /** 라인 금액 = 단가 스냅샷 * 수량. */
    public BigDecimal amount() {
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * 예약 반영(가산). 전량 확보면 STOCK, 부족분 남으면 BACKORDERED(소스 판정은 procurement).
     */
    public void applyReservation(int reservedDelta, String sourceWarehouseCode) {
        this.reservedQuantity = Math.min(quantity, this.reservedQuantity + Math.max(0, reservedDelta));
        this.fulfillmentSource = (this.reservedQuantity >= quantity)
                ? FulfillmentSource.STOCK : FulfillmentSource.BACKORDERED;
        if (sourceWarehouseCode != null) {
            this.fromWarehouseCode = sourceWarehouseCode;   // 재고 확보분의 출발지(출고창고) 기록
        }
    }

    /**
     * 영속 복원 적용(reconstitute): 저장된 상태를 그대로 되살린다(파생 안 함).
     * applyReservation은 confirm/refulfill 라이브 경로용(STOCK/BACKORDERED 파생)이라
     * 미확정(source=null) 라인을 BACKORDERED 로 오염시키므로 복원엔 쓰지 않는다.
     */
    public void restore(int reservedQuantity, FulfillmentSource fulfillmentSource, String fromWarehouseCode) {
        this.reservedQuantity = reservedQuantity;
        this.fulfillmentSource = fulfillmentSource; // NULL/STOCK/BACKORDERED 그대로
        this.fromWarehouseCode = fromWarehouseCode;
    }

    public boolean fullyReserved() { return reservedQuantity >= quantity; }
    public int shortfall() { return quantity - reservedQuantity; }

    public int lineNo() { return lineNo; }
    public String sku() { return sku; }
    public String nameSnapshot() { return nameSnapshot; }
    public BigDecimal unitPriceSnapshot() { return unitPriceSnapshot; }
    public int quantity() { return quantity; }
    public int reservedQuantity() { return reservedQuantity; }
    public FulfillmentSource fulfillmentSource() { return fulfillmentSource; }
    public String fromWarehouseCode() { return fromWarehouseCode; }
}
