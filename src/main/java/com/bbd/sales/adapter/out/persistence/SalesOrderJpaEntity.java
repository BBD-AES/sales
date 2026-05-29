package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 출고 요청 영속 엔티티(애그리거트 영속 모델).
 *
 * @Version: 낙관적 락. 동시에 두 번 수령(receive) 같은 경합에서 두 번째 커밋이 터지게 해
 *           "재고 이중 이동"을 막는다. (이전 리뷰에서 지적한 동시성 구멍 보강)
 */
@Entity
@Table(name = "sales_order")
@Getter
@Setter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class SalesOrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "so_number", nullable = false, updatable = false, unique = true)
    private String soNumber;

    private String fromWarehouseCode;
    private String toWarehouseCode;

    @Enumerated(EnumType.STRING)
    private SalesOrderStatus status;

    @Enumerated(EnumType.STRING)
    private SalesOrderPriority priority;

    @Column(length = 1000)
    private String note;

    private String requestedBy;
    private String approvedBy;
    private String rejectedBy;
    private String receivedBy;
    private String canceledBy;
    @Column(length = 1000)
    private String rejectReason;

    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime canceledAt;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesOrderLineJpaEntity> lines = new ArrayList<>();

    /** 라인 전체 교체(양방향 동기화 포함). orphanRemoval 로 기존 라인은 자동 삭제. */
    public void replaceLines(List<SalesOrderLineJpaEntity> newLines) {
        this.lines.clear();
        for (SalesOrderLineJpaEntity line : newLines) {
            line.setSalesOrder(this);
            this.lines.add(line);
        }
    }
}
