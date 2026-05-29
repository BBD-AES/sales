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
 *  - @Entity 도, @Service 도, ApiException 도 여기엔 없다.
 *  - 따라서 스프링 컨텍스트 없이 순수 단위테스트가 가능하다.
 *  - JPA 영속화는 adapter.out.persistence 의 별도 JpaEntity + 매퍼가 담당한다.
 *    (도메인 모델과 영속 모델을 분리 -> 도메인이 ORM 사정에 끌려다니지 않음)
 *
 * 상태 전이 '업무 규칙'은 전부 이 안에 있다. 서비스는 이 메서드를 호출만 한다.
 * 반면 '누가' 할 수 있는지(권한: 역할/소속창고)는 도메인이 아니라 application 서비스 책임.
 *
 * 창고 방향 규칙(기존 설계 유지):
 *   fromWarehouseCode = 요청 지점(목적지),  toWarehouseCode = HQ(출발지)
 *   => 수령 시 재고 이동은 source=toWarehouseCode(HQ) -> destination=fromWarehouseCode(지점)
 */
public class SalesOrder {

    private final String soNumber;            // 업무 식별자(도메인 정체성). DB PK 는 도메인이 모른다.
    private final String fromWarehouseCode;   // 지점(목적지)
    private final String toWarehouseCode;     // HQ(출발지)

    private SalesOrderStatus status;
    private SalesOrderPriority priority;
    private String note;

    private final List<SalesOrderLine> lines = new ArrayList<>();

    // 액터/타임스탬프 (감사 추적용)
    private String requestedBy;
    private String approvedBy;
    private String rejectedBy;
    private String receivedBy;
    private String canceledBy;
    private String rejectReason;

    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime canceledAt;

    private SalesOrder(String soNumber, String fromWarehouseCode, String toWarehouseCode) {
        this.soNumber = soNumber;
        this.fromWarehouseCode = fromWarehouseCode;
        this.toWarehouseCode = toWarehouseCode;
    }

    /**
     * 신규 출고 요청 생성(팩토리). 생성과 동시에 REQUESTED.
     * 스냅샷이 채워진 라인을 받는다(스냅샷 조회는 서비스가 포트로 끝낸 뒤 넘겨줌).
     */
    public static SalesOrder request(String soNumber,
                                     String fromWarehouseCode,
                                     String toWarehouseCode,
                                     SalesOrderPriority priority,
                                     String note,
                                     List<SalesOrderLine> lines,
                                     String requesterEmployeeNumber,
                                     LocalDateTime now) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("출고 요청 라인은 최소 1개 이상이어야 합니다.");
        }
        SalesOrder so = new SalesOrder(soNumber, fromWarehouseCode, toWarehouseCode);
        so.priority = priority == null ? SalesOrderPriority.NORMAL : priority;
        so.note = note;
        so.lines.addAll(lines);
        so.status = SalesOrderStatus.REQUESTED;
        so.requestedBy = requesterEmployeeNumber;
        so.requestedAt = now;
        return so;
    }

    /**
     * 영속 계층에서 읽어온 값으로 도메인 객체를 '복원'하는 팩토리.
     * 규칙 검증 없이 그대로 재구성한다(이미 DB 에 있던 유효한 상태이므로).
     * adapter.out.persistence 매퍼 전용.
     */
    public static SalesOrder reconstitute(String soNumber, String fromWarehouseCode, String toWarehouseCode,
                                          SalesOrderStatus status, SalesOrderPriority priority, String note,
                                          List<SalesOrderLine> lines,
                                          String requestedBy, String approvedBy, String rejectedBy,
                                          String receivedBy, String canceledBy, String rejectReason,
                                          LocalDateTime requestedAt, LocalDateTime approvedAt, LocalDateTime rejectedAt,
                                          LocalDateTime receivedAt, LocalDateTime canceledAt) {
        SalesOrder so = new SalesOrder(soNumber, fromWarehouseCode, toWarehouseCode);
        so.status = status;
        so.priority = priority;
        so.note = note;
        if (lines != null) so.lines.addAll(lines);
        so.requestedBy = requestedBy;
        so.approvedBy = approvedBy;
        so.rejectedBy = rejectedBy;
        so.receivedBy = receivedBy;
        so.canceledBy = canceledBy;
        so.rejectReason = rejectReason;
        so.requestedAt = requestedAt;
        so.approvedAt = approvedAt;
        so.rejectedAt = rejectedAt;
        so.receivedAt = receivedAt;
        so.canceledAt = canceledAt;
        return so;
    }

    // ---------------------------------------------------------------
    // 상태 전이 (업무 규칙). 전이 불가하면 도메인 예외를 던진다.
    // ---------------------------------------------------------------

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

    /** 취소. REQUESTED 에서만(요청자 본인이 회수). */
    public void cancel(String actor, LocalDateTime now) {
        if (status != SalesOrderStatus.REQUESTED) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.NOT_CANCELABLE);
        }
        this.status = SalesOrderStatus.CANCELED;
        this.canceledBy = actor;
        this.canceledAt = now;
    }

    /** 승인. REQUESTED 에서만(HQ 결정). */
    public void approve(String actor, LocalDateTime now) {
        if (!status.isDecidable()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.NOT_DECIDABLE);
        }
        this.status = SalesOrderStatus.APPROVED;
        this.approvedBy = actor;
        this.approvedAt = now;
    }

    /** 반려. REQUESTED 에서만, 사유 필수. */
    public void reject(String actor, String reason, LocalDateTime now) {
        if (!status.isDecidable()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.NOT_DECIDABLE);
        }
        if (reason == null || reason.isBlank()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.REJECT_REASON_REQUIRED);
        }
        this.status = SalesOrderStatus.REJECTED;
        this.rejectedBy = actor;
        this.rejectReason = reason;
        this.rejectedAt = now;
    }

    /** 수령. APPROVED 에서만. 실재고 이동은 서비스가 포트로 처리하고, 여기선 상태만 닫는다. */
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
        return lines.stream()
                .map(SalesOrderLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 요청 지점(목적지) 소속 사용자만 접근 가능한지 판단할 때 쓰는 키. */
    public boolean ownedByWarehouse(String warehouseCode) {
        return fromWarehouseCode.equals(warehouseCode);
    }

    // --- 조회용 getter (불변 노출) ---
    public String soNumber() { return soNumber; }
    public String fromWarehouseCode() { return fromWarehouseCode; }
    public String toWarehouseCode() { return toWarehouseCode; }
    public SalesOrderStatus status() { return status; }
    public SalesOrderPriority priority() { return priority; }
    public String note() { return note; }
    public List<SalesOrderLine> lines() { return Collections.unmodifiableList(lines); }
    public String requestedBy() { return requestedBy; }
    public String approvedBy() { return approvedBy; }
    public String rejectedBy() { return rejectedBy; }
    public String receivedBy() { return receivedBy; }
    public String canceledBy() { return canceledBy; }
    public String rejectReason() { return rejectReason; }
    public LocalDateTime requestedAt() { return requestedAt; }
    public LocalDateTime approvedAt() { return approvedAt; }
    public LocalDateTime rejectedAt() { return rejectedAt; }
    public LocalDateTime receivedAt() { return receivedAt; }
    public LocalDateTime canceledAt() { return canceledAt; }
}
