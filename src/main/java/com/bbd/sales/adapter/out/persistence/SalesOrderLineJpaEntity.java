package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.domain.FulfillmentSource;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 라인 영속 엔티티. 도메인 SalesOrderLine 과 의도적으로 '분리'된 별도 클래스.
 * (도메인이 JPA 어노테이션/지연로딩 같은 ORM 사정에 오염되지 않게 하기 위함)
 */
@Entity
@Table(name = "sales_order_line",
        uniqueConstraints = @UniqueConstraint(name = "uq_sales_order_line", columnNames = {"so_number", "line_no"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class SalesOrderLineJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "so_number")   // FK -> sales_order.so_number (물리 PK)
    @Setter(AccessLevel.PACKAGE)
    private SalesOrderJpaEntity salesOrder;

    private int lineNo;
    private String sku;
    private String nameSnapshot;
    private BigDecimal unitPriceSnapshot;
    private int quantity;

    // ---라인레벨 충족추적---
    @Setter(AccessLevel.PACKAGE)
    private int reservedQuantity;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.PACKAGE)
    private FulfillmentSource fulfillmentSource;

    public SalesOrderLineJpaEntity(int lineNo, String sku, String nameSnapshot,
                                   BigDecimal unitPriceSnapshot, int quantity) {
        this.lineNo = lineNo;
        this.sku = sku;
        this.nameSnapshot = nameSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.quantity = quantity;
    }

    /** lineNo(매칭 키)는 유지하고 내용/충족 필드만 기존 행에 복사(행 재사용 -> UPDATE). */
    void copyMutableForm(SalesOrderLineJpaEntity src) {
        this.sku = src.sku;
        this.nameSnapshot = src.nameSnapshot;
        this.unitPriceSnapshot = src.unitPriceSnapshot;
        this.quantity = src.quantity;
        this.reservedQuantity = src.reservedQuantity;
        this.fulfillmentSource = src.fulfillmentSource;
    }
}
