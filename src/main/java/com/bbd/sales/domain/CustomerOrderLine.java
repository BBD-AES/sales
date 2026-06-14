package com.bbd.sales.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 수주 라인(값 객체). 상품명,단가는 수주 시점 스냅샷
 */
public class CustomerOrderLine {
    private final int lineNo;
    private final String sku;
    private final String nameSnapshot;
    private final BigDecimal unitPriceSnapshot;
    private final int quantity;

    public CustomerOrderLine(int lineNo, String sku, String nameSnapshot, BigDecimal unitPriceSnapshot, int quantity) {
        // 상태위반 검증이 아닌 입력값 예외이므로 CustomerOrderStateException이 아닌 IllegalArgumentException
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("sku 는 필수입니다.");
        if (quantity < 1) throw new IllegalArgumentException("quantity 는 1 이상이어야 합니다.");
        this.lineNo = lineNo;
        this.sku = sku;
        this.nameSnapshot = nameSnapshot;
        // null이면 기본값 넣기
        this.unitPriceSnapshot = Objects.requireNonNullElse(unitPriceSnapshot, BigDecimal.ZERO);
        this.quantity = quantity;
    }

    public BigDecimal amount() {
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }

    public int lineNo() {
        return lineNo;
    }

    public String sku() {
        return sku;
    }

    public String nameSnapshot() {
        return nameSnapshot;
    }

    public BigDecimal unitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public int quantity() {
        return quantity;
    }
}
