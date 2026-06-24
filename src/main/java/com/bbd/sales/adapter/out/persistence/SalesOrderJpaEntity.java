package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 출고 요청 영속 엔티티(애그리거트 영속 모델).
 *
 * @Version: 낙관적 락. 동시 수령(receive) 경합에서 두 번째 커밋을 터지게 해 "재고 이중 이동"을 막는다.
 */
@Entity
@Table(name = "sales_order")
@Getter
@Setter(AccessLevel.PACKAGE) // 가변 컬럼은 패키지 전용. 매퍼만 접근, 외부 노출 금지
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesOrderJpaEntity {

    // 물리 PK = so_number(업무 식별자). surrogate id 제거(팀 결정: id = so_number).
    @Id
    @Column(name = "so_number", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String soNumber;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    @Setter(AccessLevel.NONE) private String toWarehouseCode;
    @Setter(AccessLevel.NONE) private String toWarehouseName;   // 생성 시점 스냅샷(DBML 컬럼 to_warehouse_name)

    // ---가변컬럼---
    @Enumerated(EnumType.STRING)
    private SalesOrderStatus status;

    @Enumerated(EnumType.STRING)
    private SalesOrderPriority priority;

    @Column(length = 1000)
    private String note;

    @Column(name = "customer_order_number", length = 40)
    private String customerOrderNumber;   // 연계 고객주문(CO) 번호 — 선택. STR 이 어느 CO 를 채우는지.

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

    // 컬렉션은 replaceLines()로만 바꿀 수 있음
    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100) // #36: 목록 N행의 lines 를 IN 배치로 일괄 로드 → N+1 제거(페이징 SQL 불변, 인메모리 페이징 함정 없음)
    @Setter(AccessLevel.NONE)
    private List<SalesOrderLineJpaEntity> lines = new ArrayList<>();

    /** INSERT 전용 생성자: 불변 식별자 + 스냅샷. 가변 컬럼은 applyTo가 채움*/
    public SalesOrderJpaEntity(String soNumber, String toWarehouseCode, String toWarehouseName) {
        this.soNumber = soNumber;
        this.toWarehouseCode = toWarehouseCode;
        this.toWarehouseName = toWarehouseName;
    }

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
        // lineNo 기준 in-place 병합.
        // 요청 라인은 서비스에서 1부터 다시 번호가 매겨진다.
        // 따라서 여기서의 lineNo는 "라인의 영구 식별자"라기보다 현재 주문 라인의 순서 슬롯에 가깝다.

        // clear() + add()로 전체 교체하지 않는다.(금지)
        // Hibernate가 기존 라인 DELETE보다 새 라인 INSERT를 먼저 flush할 수 있고,
        // 이 경우 같은 (so_number, line_no)가 잠시 중복되어 uq_sales_order_line 제약에 걸릴 수 있다.

        // 기존 영속 라인을 lineNo 슬롯 기준으로 찾기 위한 map.
        // 같은 lineNo 슬롯이 새 요청에도 있으면 기존 row를 재사용해 UPDATE한다.
        Map<Integer, SalesOrderLineJpaEntity> existing = new HashMap<>();
        for (SalesOrderLineJpaEntity l : this.lines) existing.put(l.getLineNo(), l);

        // 새 요청이 차지하는 lineNo 슬롯 집합.
        Set<Integer> incoming = new HashSet<>();
        for (SalesOrderLineJpaEntity in : newLines) incoming.add(in.getLineNo());

        // 새 요청에 포함되지 않은 lineNo 슬롯 제거.
        // orphanRemoval=true라서 컬렉션에서 빠진 managed child는 flush 시 DELETE된다.
        this.lines.removeIf(l -> !incoming.contains(l.getLineNo())); // 빠진 라인 -> orphan DELETE

        // removeIf 이후 this.lines에는 새 요청에도 남아 있는 기존 라인만 남는다.
        // 따라서 이 시점의 this.lines 개수는 newLines 개수보다 클 수 없다.
        for (SalesOrderLineJpaEntity in : newLines) {
            SalesOrderLineJpaEntity ex = existing.get(in.getLineNo());
            if (ex == null) {
                // 기존에 없던 lineNo 슬롯이면 새 row로 추가한다.
                // 예: 기존 2개에서 새 요청 3개로 늘어난 경우 lineNo=3.
                in.setSalesOrder(this);
                this.lines.add(in);
            } else {
                // 기존에 있던 lineNo 슬롯이면 row를 재사용하고 내용만 갱신한다.
                // SKU가 같다는 뜻이 아니다. 같은 순서 위치의 row를 UPDATE하는 것이다.
                // 예: 기존 2번 라인의 SKU가 B였고 새 요청의 2번 라인이 C여도,
                // DB row는 유지되고 sku/name/price/quantity만 UPDATE된다.
                ex.copyMutableForm(in);
            }
        }
    }
}
