package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.domain.CustomerOrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_order")
@Getter
@Setter(AccessLevel.PACKAGE) // 도메인 객체 재조립 시 setter 사용 위해 package-private
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrderJpaEntity {
    @Id
    @Column(name = "co_number", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String coNumber;

    // 낙관적 락
    // @Version은 Hibernate가 flush 시점에 직접 증가/검증함.
    // 동시 confirm/cancel 경합 감지 위해 setter를 노출하지 않음
    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    // 불변 스냅샷
    @Setter(AccessLevel.NONE) private String dealerWarehouseCode;
    @Setter(AccessLevel.NONE) private String dealerName;
    @Setter(AccessLevel.NONE) private String customerName;
    @Setter(AccessLevel.NONE) private String customerContact;

    // 가변 컬럼(setter 적용)
    @Enumerated(EnumType.STRING)
    private CustomerOrderStatus status;

    @Column(length = 1000)
    private String note;

    private String requestedBy;
    private String confirmedBy;
    private String canceledBy;
    private String closedBy;

    private LocalDateTime requestedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime closedAt;

    // Setter 없이 컬렉션 변경은 replaceLines()로만 하도록 강제
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "customerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerOrderLineJpaEntity> lines = new ArrayList<>();

    public void replaceLines(List<CustomerOrderLineJpaEntity> newLines) {
        this.lines.clear();
        for (CustomerOrderLineJpaEntity line : newLines) {
            line.setCustomerOrder(this);
            this.lines.add(line);
        }
    }

    /**
     * INSERT 전용 생성자: 불변 식별자 + 스냅샷을 set-once로 고정한다.
     * 가변 컬럼(상태/타임스탭프/라인)은 이후 매퍼 applyTo가 채운다.
     */
    public CustomerOrderJpaEntity(String coNumber, String dealerWarehouseCode, String dealerName, String customerName, String customerContact) {
        this.coNumber = coNumber;
        this.dealerWarehouseCode = dealerWarehouseCode;
        this.dealerName = dealerName;
        this.customerName = customerName;
        this.customerContact = customerContact;
    }
}
