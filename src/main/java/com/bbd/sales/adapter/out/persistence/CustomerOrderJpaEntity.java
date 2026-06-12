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
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrderJpaEntity {
    @Id
    @Column(name = "co_number", nullable = false, updatable = false)
    private String coNumber;

    @Version
    private Long version;

    private String dealerWarehouseCode;
    private String dealerName;
    private String customerName;
    private String customerContact;

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

    @OneToMany(mappedBy = "customerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerOrderLineJpaEntity> lines = new ArrayList<>();

    public void replaceLines(List<CustomerOrderLineJpaEntity> newLines) {
        this.lines.clear();
        for (CustomerOrderLineJpaEntity line : newLines) {
            line.setCustomerOrder(this);
            this.lines.add(line);
        }
    }

}
