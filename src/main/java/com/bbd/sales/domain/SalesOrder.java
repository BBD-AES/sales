package com.bbd.sales.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 출고 요청 애그리거트 루트 (도메인 코어).
 *
 * 헥사고날의 가장 안쪽: 프레임워크/JPA/스프링 의존이 0 이다.
 *  - @Entity 도, @Service 도, ApiException 도 여기엔 없다 -> 스프링 없이 순수 단위테스트 가능.
 *  - JPA 영속화는 adapter.out.persistence 의 별도 JpaEntity + 매퍼가 담당(도메인-영속 모델 분리).
 *  - @Transactional 도 붙이지 않는다. 트랜잭션 경계는 application.service.SalesOrderService 가 잡고,
 *    이 객체는 그 트랜잭션 안에서 상태 전이 규칙만 수행하는 순수 도메인 모델이다.
 *
 * 창고 방향(시드 데이터 SO-2026-0001 확인): from=요청 지점(목적지), to=HQ(출발지).
 *  => 수령 시 재고 이동 source=to(HQ) -> destination=from(지점).
 *
 * 창고명(fromWarehouseName/toWarehouseName)은 '생성 시점 스냅샷'으로 보관한다.
 *  MSA(DB-per-service)라 창고 마스터는 Inventory 서비스 소유 -> 조회마다 원격 호출하면
 *  목록 한 페이지에 행 수만큼 호출이 터진다. 생성 때 한 번 박아두면 읽기는 호출 0,
 *  창고명이 후에 바뀌어도 거래문서의 과거 이름이 보존된다(단가·상품명 스냅샷과 동일 논리).
 */
public class SalesOrder {

    private final String soNumber;            // 업무 식별자(도메인 정체성). DB PK 는 도메인이 모른다.
    private final String fromWarehouseCode;   // 지점(목적지)
    private final String fromWarehouseName;   // 생성 시점 스냅샷
    private final String toWarehouseCode;     // HQ(출발지)
    private final String toWarehouseName;     // 생성 시점 스냅샷

    private SalesOrderStatus status;
    private SalesOrderPriority priority;
    private String note;

    private final List<SalesOrderLine> lines = new ArrayList<>();

    // 액터/타임스탬프 (감사 추적용). rejectedBy/rejectedAt 은 팀 결정으로 보관.
    private String requestedBy;
    private String approvedBy;
    private String rejectedBy;
    private String receivedBy;
    private String canceledBy;
    private String rejectedReason;

    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime canceledAt;

    private SalesOrder(String soNumber,
                       String fromWarehouseCode, String fromWarehouseName,
                       String toWarehouseCode, String toWarehouseName) {
        this.soNumber = soNumber;
        this.fromWarehouseCode = fromWarehouseCode;
        this.fromWarehouseName = fromWarehouseName;
        this.toWarehouseCode = toWarehouseCode;
        this.toWarehouseName = toWarehouseName;
    }

    /** 신규 출고 요청 생성(팩토리). 생성과 동시에 REQUESTED. 스냅샷(상품/창고명)은 서비스가 채워 넘김. */
    public static SalesOrder request(String soNumber,
                                     String fromWarehouseCode, String fromWarehouseName,
                                     String toWarehouseCode, String toWarehouseName,
                                     SalesOrderPriority priority,
                                     String note,
                                     List<SalesOrderLine> lines,
                                     String requesterEmployeeNumber,
                                     LocalDateTime now) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("출고 요청 라인은 최소 1개 이상이어야 합니다.");
        }
        SalesOrder so = new SalesOrder(soNumber, fromWarehouseCode, fromWarehouseName, toWarehouseCode, toWarehouseName);
        so.priority = priority == null ? SalesOrderPriority.NORMAL : priority;
        so.note = note;
        so.lines.addAll(lines);
        so.status = SalesOrderStatus.REQUESTED;
        so.requestedBy = requesterEmployeeNumber;
        so.requestedAt = now;
        return so;
    }

    /** 영속 계층에서 읽어온 값으로 도메인 객체를 무검증 복원(persistence 매퍼 전용). */
    public static SalesOrder reconstitute(String soNumber,
                                          String fromWarehouseCode, String fromWarehouseName,
                                          String toWarehouseCode, String toWarehouseName,
                                          SalesOrderStatus status, SalesOrderPriority priority, String note,
                                          List<SalesOrderLine> lines,
                                          String requestedBy, String approvedBy, String rejectedBy,
                                          String receivedBy, String canceledBy, String rejectedReason,
                                          LocalDateTime requestedAt, LocalDateTime approvedAt, LocalDateTime rejectedAt,
                                          LocalDateTime receivedAt, LocalDateTime canceledAt) {
        SalesOrder so = new SalesOrder(soNumber, fromWarehouseCode, fromWarehouseName, toWarehouseCode, toWarehouseName);
        so.status = status;
        so.priority = priority;
        so.note = note;
        if (lines != null) so.lines.addAll(lines);
        so.requestedBy = requestedBy;
        so.approvedBy = approvedBy;
        so.rejectedBy = rejectedBy;
        so.receivedBy = receivedBy;
        so.canceledBy = canceledBy;
        so.rejectedReason = rejectedReason;
        so.requestedAt = requestedAt;
        so.approvedAt = approvedAt;
        so.rejectedAt = rejectedAt;
        so.receivedAt = receivedAt;
        so.canceledAt = canceledAt;
        return so;
    }

    // ─────────────────────── 상태 전이 (업무 규칙) ───────────────────────
    //
    // Q. 왜 이 메서드들엔 @Transactional 을 안 붙이나?
    //  1) 여기선 DB 를 건드리지 않는다. 메모리 위 필드(status 등)만 바꾼다.
    //     커밋/롤백할 대상이 없으니 트랜잭션이라는 개념 자체가 필요 없다.
    //  2) 트랜잭션 경계는 application 의 SalesOrderService(@Transactional)가 소유한다.
    //     이 메서드는 이미 열려 있는 그 트랜잭션 '안에서' 도는 한 스텝일 뿐이고,
    //     실제 DB 반영(UPDATE)은 서비스 트랜잭션이 커밋될 때 Hibernate 가 flush 한다.
    //  3) 애초에 @Transactional 은 스프링 빈(프록시)에만 동작한다. SalesOrder 는
    //     new / reconstitute 로 만드는 평범한 객체(빈 아님)라 붙여도 무시된다.
    //     게다가 순수 도메인에 스프링을 끌어들이면 헥사고날 경계가 깨진다.
    //
    // 정리: 도메인은 "무엇이 올바른 상태 변화인가"만 책임지고,
    //       "그 변화를 언제 DB 에 원자적으로 확정하나"는 서비스의 @Transactional 이 책임진다.

    /** 내용 수정. REQUESTED 에서만. newLines == null 이면 라인 유지, 아니면 전체 교체. */
    public void updateContents(SalesOrderPriority priority, String note, List<SalesOrderLine> newLines) {
        if (!status.isEditable()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.NOT_EDITABLE);
        }
        if (priority != null) this.priority = priority;
        this.note = note;
        if (newLines != null) {
            if (newLines.isEmpty()) {
                throw new IllegalArgumentException("라인을 교체할 경우 최소 1개 이상이어야 합니다.");
            }
            this.lines.clear();
            this.lines.addAll(newLines);
        }
    }

    /**
     * HQ로 제출. REQUESTED 에서만(BRANCH_MANAGER 가 "실제로 HQ에 올린다" 결정).
     * TODO(audit): 제출자(submittedBy/submittedAt) 기록 컬럼은 별도 커밋에서 추가.
     */
    public void submit(LocalDateTime now) {
        if (!status.canSubmit()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.NOT_SUBMITTABLE);
        }
        this.status = SalesOrderStatus.SUBMITTED;
    }

    /** 취소. REQUESTED/SUBMITTED 에서만(요청자 본인 회수, HQ 손에 넘어가기 전까지). */
    public void cancel(String actor, LocalDateTime now) {
        if (!status.isCancelable()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.NOT_CANCELABLE);
        }
        this.status = SalesOrderStatus.CANCELED;
        this.canceledBy = actor;
        this.canceledAt = now;
    }

    /**
     * HQ 승인 -> 충족 진행. SUBMITTED 에서만.
     * 재고 확인 결과에 따라 BACKORDERED 로 분기하는 로직은 InventoryPort 연동 커밋에서 추가한다.
     * 현재는 낙관적으로 바로 IN_FULFILLMENT 로 둔다.
     */
    public void approveByHq(String actor, LocalDateTime now) {
        if (!status.canHqDecide()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.NOT_DECIDABLE);
        }
        this.status = SalesOrderStatus.IN_FULFILLMENT;
        this.approvedBy = actor;
        this.approvedAt = now;
    }

    /** 반려. SUBMITTED 에서만(HQ 결정), 사유 필수. */
    public void reject(String actor, String reason, LocalDateTime now) {
        if (!status.canHqDecide()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.NOT_DECIDABLE);
        }
        if (reason == null || reason.isBlank()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.REJECT_REASON_REQUIRED);
        }
        this.status = SalesOrderStatus.REJECTED;
        this.rejectedBy = actor;
        this.rejectedReason = reason;
        this.rejectedAt = now;
    }

    /** 수령. IN_FULFILLMENT 에서만. 실재고 이동은 서비스가 포트로 처리, 여기선 상태만 닫는다. */
    public void receive(String actor, LocalDateTime now) {
        if (!status.isReceivable()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.NOT_RECEIVABLE);
        }
        this.status = SalesOrderStatus.RECEIVED;
        this.receivedBy = actor;
        this.receivedAt = now;
    }

    /** 총액 = 라인 금액 합. */
    public BigDecimal totalAmount() {
        return lines.stream().map(SalesOrderLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 본인 소속 창고(요청 지점) 자원인지 판단 키. */
    public boolean ownedByWarehouse(String warehouseCode) {
        return fromWarehouseCode.equals(warehouseCode);
    }

    // --- 조회용 getter (불변 노출) ---
    public String soNumber() { return soNumber; }
    public String fromWarehouseCode() { return fromWarehouseCode; }
    public String fromWarehouseName() { return fromWarehouseName; }
    public String toWarehouseCode() { return toWarehouseCode; }
    public String toWarehouseName() { return toWarehouseName; }
    public SalesOrderStatus status() { return status; }
    public SalesOrderPriority priority() { return priority; }
    public String note() { return note; }
    public List<SalesOrderLine> lines() { return Collections.unmodifiableList(lines); }
    public String requestedBy() { return requestedBy; }
    public String approvedBy() { return approvedBy; }
    public String rejectedBy() { return rejectedBy; }
    public String receivedBy() { return receivedBy; }
    public String canceledBy() { return canceledBy; }
    public String rejectedReason() { return rejectedReason; }
    public LocalDateTime requestedAt() { return requestedAt; }
    public LocalDateTime approvedAt() { return approvedAt; }
    public LocalDateTime rejectedAt() { return rejectedAt; }
    public LocalDateTime receivedAt() { return receivedAt; }
    public LocalDateTime canceledAt() { return canceledAt; }
}
