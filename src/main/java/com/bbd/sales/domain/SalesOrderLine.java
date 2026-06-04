package com.bbd.sales.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 출고 요청 라인. 도메인 값 객체.
 *
 * nameSnapshot / unitPriceSnapshot 은 "주문 시점의" 상품명·단가를 박제한 값이다.
 * 상품 마스터가 나중에 바뀌어도 과거 주문 금액은 변하면 안 되므로 스냅샷으로 보관한다.
 * 스냅샷 원본을 어디서 가져오는지(상품/재고 컨텍스트)는 도메인이 알 바 아니고,
 * application 서비스가 out 포트(CatalogPort)로 조회해 채워 넣는다.
 */
public class SalesOrderLine {

    private final int lineNo;
    private final String sku;
    private final String nameSnapshot;
    private final BigDecimal unitPriceSnapshot;
    private final int quantity;

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
        this.unitPriceSnapshot = Objects.requireNonNullElse(unitPriceSnapshot, BigDecimal.ZERO);
        this.quantity = quantity;
    }

    /** 라인 금액 = 단가 스냅샷 * 수량. */
    public BigDecimal amount() {
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }

    public int lineNo() { return lineNo; }
    public String sku() { return sku; }
    public String nameSnapshot() { return nameSnapshot; }
    public BigDecimal unitPriceSnapshot() { return unitPriceSnapshot; }
    public int quantity() { return quantity; }
}
