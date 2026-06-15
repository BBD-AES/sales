package com.bbd.sales.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_order_line", uniqueConstraints = @UniqueConstraint(name = "uq_customer_order_line", columnNames = {"co_number", "line_no"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CustomerOrderLineJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "co_number") // FK -> customer_order.co_number
    @Setter(AccessLevel.PACKAGE)
    private CustomerOrderJpaEntity customerOrder;

    // 불변(스냅샷)
    private int lineNo;
    private String sku;
    private String nameSnapshot;
    private BigDecimal unitPriceSnapshot;
    private int quantity;

    public CustomerOrderLineJpaEntity(int lineNo, String sku, String nameSnapshot, BigDecimal unitPriceSnapshot, int quantity) {
        this.lineNo = lineNo;
        this.sku = sku;
        this.nameSnapshot = nameSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.quantity = quantity;
    }
}
