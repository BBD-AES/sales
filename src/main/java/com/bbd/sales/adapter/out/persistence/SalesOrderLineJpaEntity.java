package com.bbd.sales.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 라인 영속 엔티티. 도메인 SalesOrderLine 과 의도적으로 '분리'된 별도 클래스.
 * (도메인이 JPA 어노테이션/지연로딩 같은 ORM 사정에 오염되지 않게 하기 위함)
 */
@Entity
@Table(name = "sales_order_line")
@Getter
@Setter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class SalesOrderLineJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id")
    private SalesOrderJpaEntity salesOrder;

    private int lineNo;
    private String sku;
    private String nameSnapshot;
    private BigDecimal unitPriceSnapshot;
    private int quantity;

    public SalesOrderLineJpaEntity(int lineNo, String sku, String nameSnapshot,
                                   BigDecimal unitPriceSnapshot, int quantity) {
        this.lineNo = lineNo;
        this.sku = sku;
        this.nameSnapshot = nameSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.quantity = quantity;
    }
}
