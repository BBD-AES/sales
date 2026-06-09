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
 * @Version: 낙관적 락. 동시 수령(receive) 경합에서 두 번째 커밋을 터지게 해 "재고 이중 이동"을 막는다.
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

    private String toWarehouseCode;
    private String toWarehouseName;   // 생성 시점 스냅샷(DBML 컬럼 to_warehouse_name)

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

    @Column(name = "rejected_reason", length = 1000)
    private String rejectedReason;

    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime canceledAt;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesOrderLineJpaEntity> lines = new ArrayList<>();

    /**
     * 라인 전체 교체(양방향 동기화 포함).
     *
     * Q. 왜 save 가 아니라 set 인가? (작업 중 메모 답)
     * A. 이 엔티티는 이미 영속성 컨텍스트가 '관리(managed)'하는 상태다. 관리 상태에서는
     *    필드를 바꿔 두면 트랜잭션 커밋(flush) 시점에 Hibernate 가 dirty checking 으로
     *    자동 UPDATE/INSERT/DELETE 를 만든다. cascade=ALL + orphanRemoval=true 라서
     *    리스트에서 빠진 기존 라인은 DELETE, 새로 add 된 라인은 INSERT 가 자동 발생.
     *    => 라인마다 repository.save() 를 부를 필요가 없다. 양방향 참조(set)만 맞춰주면 끝.
     *
     * Q. 왜 @Transactional 을 여기 붙이지 않는가?
     * A. 트랜잭션 경계는 엔티티가 아니라 application service 가 잡는다. 이 메서드는
     *    DB 작업을 직접 시작하는 메서드가 아니라, 이미 열린 트랜잭션 안에서 관리 중인
     *    엔티티의 컬렉션 상태만 바꾸는 도메인/영속 모델 보조 메서드다. 엔티티에
     *    @Transactional 을 붙이면 영속 모델이 Spring 트랜잭션에 의존하게 되어 경계가
     *    흐려진다. 호출자는 @Transactional 메서드 안에서 이 메서드를 호출해야 한다.
     */
    public void replaceLines(List<SalesOrderLineJpaEntity> newLines) {
        this.lines.clear();
        for (SalesOrderLineJpaEntity line : newLines) {
            line.setSalesOrder(this);
            this.lines.add(line);
        }
    }
}
