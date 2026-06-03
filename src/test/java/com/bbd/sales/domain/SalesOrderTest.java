package com.bbd.sales.domain;

import com.bbd.sales.domain.SalesOrderStateException.Violation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SalesOrder 애그리거트 상태전이 단위테스트.
 * 헥사고날 효과 확인용: 스프링/JPA/DB 없이 순수 객체만으로 업무 규칙을 검증한다.
 */
class SalesOrderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 3, 10, 0);

    // ---------- 상태별 픽스처 ----------

    private SalesOrder requested() {
        return SalesOrder.request(
                "SO-2026-0001", "BR01", "강남지점",
                SalesOrderPriority.NORMAL, "메모",
                List.of(new SalesOrderLine(1, "SKU-1", "볼트", new BigDecimal("100"), 3)),
                "EMP-staff", NOW);
    }

    private SalesOrder submitted() {
        SalesOrder so = requested();
        so.submit(NOW);
        return so;
    }

    private SalesOrder inFulfillment() {
        SalesOrder so = submitted();
        so.approveByHq("EMP-hq", NOW);
        return so;
    }

    private SalesOrder backordered() {
        SalesOrder so = submitted();
        so.backorder("EMP-hq", NOW);
        return so;
    }

    // ---------- 생성 ----------

    @Test
    @DisplayName("생성하면 REQUESTED, 요청자/총액/라인이 채워진다")
    void request_createsRequested() {
        SalesOrder so = requested();
        assertThat(so.status()).isEqualTo(SalesOrderStatus.REQUESTED);
        assertThat(so.fromWarehouseCode()).isEqualTo("BR01");
        assertThat(so.requestedBy()).isEqualTo("EMP-staff");
        assertThat(so.requestedAt()).isEqualTo(NOW);
        assertThat(so.totalAmount()).isEqualByComparingTo("300"); // 100 * 3
        assertThat(so.lines()).hasSize(1);
    }

    @Test
    @DisplayName("라인 없이 생성하면 거부된다")
    void request_rejectsEmptyLines() {
        assertThatThrownBy(() -> SalesOrder.request(
                "SO-2026-0002", "BR01", "강남지점",
                SalesOrderPriority.NORMAL, null, List.of(), "EMP-staff", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 제출(submit) ----------

    @Test
    @DisplayName("REQUESTED -> submit -> SUBMITTED")
    void submit_fromRequested() {
        SalesOrder so = requested();
        so.submit(NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.SUBMITTED);
    }

    @Test
    @DisplayName("SUBMITTED 에서 다시 submit 하면 NOT_SUBMITTABLE")
    void submit_fromNonRequested_fails() {
        SalesOrder so = submitted();
        assertViolation(() -> so.submit(NOW), Violation.NOT_SUBMITTABLE);
    }

    // ---------- 취소(cancel) ----------

    @Test
    @DisplayName("REQUESTED/SUBMITTED 에서는 취소 가능")
    void cancel_allowedUntilSubmitted() {
        SalesOrder fromRequested = requested();
        fromRequested.cancel("EMP-staff", NOW);
        assertThat(fromRequested.status()).isEqualTo(SalesOrderStatus.CANCELED);

        SalesOrder fromSubmitted = submitted();
        fromSubmitted.cancel("EMP-mgr", NOW);
        assertThat(fromSubmitted.status()).isEqualTo(SalesOrderStatus.CANCELED);
        assertThat(fromSubmitted.canceledBy()).isEqualTo("EMP-mgr");
        assertThat(fromSubmitted.canceledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("IN_FULFILLMENT 부터는 취소 불가(NOT_CANCELABLE)")
    void cancel_afterFulfillment_fails() {
        SalesOrder so = inFulfillment();
        assertViolation(() -> so.cancel("EMP-staff", NOW), Violation.NOT_CANCELABLE);
    }

    // ---------- HQ 승인(approveByHq) ----------

    @Test
    @DisplayName("SUBMITTED -> approveByHq -> IN_FULFILLMENT, 승인자 기록")
    void approveByHq_fromSubmitted() {
        SalesOrder so = submitted();
        so.approveByHq("EMP-hq", NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.IN_FULFILLMENT);
        assertThat(so.approvedBy()).isEqualTo("EMP-hq");
        assertThat(so.approvedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("REQUESTED 에서 approveByHq 하면 NOT_DECIDABLE")
    void approveByHq_fromRequested_fails() {
        SalesOrder so = requested();
        assertViolation(() -> so.approveByHq("EMP-hq", NOW), Violation.NOT_DECIDABLE);
    }

    // ---------- 백오더(backorder) ----------

    @Test
    @DisplayName("SUBMITTED -> backorder -> BACKORDERED")
    void backorder_fromSubmitted() {
        SalesOrder so = submitted();
        so.backorder("EMP-hq", NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.BACKORDERED);
        assertThat(so.approvedBy()).isEqualTo("EMP-hq");
    }

    @Test
    @DisplayName("REQUESTED 에서 backorder 하면 NOT_DECIDABLE")
    void backorder_fromRequested_fails() {
        SalesOrder so = requested();
        assertViolation(() -> so.backorder("EMP-hq", NOW), Violation.NOT_DECIDABLE);
    }

    // ---------- 백오더 해소(fulfillFromBackorder) ----------

    @Test
    @DisplayName("BACKORDERED -> fulfillFromBackorder -> IN_FULFILLMENT")
    void fulfillFromBackorder_fromBackordered() {
        SalesOrder so = backordered();
        so.fulfillFromBackorder(NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.IN_FULFILLMENT);
    }

    @Test
    @DisplayName("SUBMITTED 에서 fulfillFromBackorder 하면 NOT_FULFILLABLE")
    void fulfillFromBackorder_fromSubmitted_fails() {
        SalesOrder so = submitted();
        assertViolation(() -> so.fulfillFromBackorder(NOW), Violation.NOT_FULFILLABLE);
    }

    // ---------- 반려(reject) ----------

    @Test
    @DisplayName("SUBMITTED -> reject(사유) -> REJECTED")
    void reject_fromSubmitted() {
        SalesOrder so = submitted();
        so.reject("EMP-hq", "예산 초과", NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.REJECTED);
        assertThat(so.rejectedReason()).isEqualTo("예산 초과");
        assertThat(so.rejectedBy()).isEqualTo("EMP-hq");
        assertThat(so.rejectedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("반려 사유가 비면 REJECT_REASON_REQUIRED")
    void reject_blankReason_fails() {
        SalesOrder so = submitted();
        assertViolation(() -> so.reject("EMP-hq", "  ", NOW), Violation.REJECT_REASON_REQUIRED);
    }

    @Test
    @DisplayName("REQUESTED 에서 reject 하면 NOT_DECIDABLE")
    void reject_fromRequested_fails() {
        SalesOrder so = requested();
        assertViolation(() -> so.reject("EMP-hq", "사유", NOW), Violation.NOT_DECIDABLE);
    }

    // ---------- 수령(receive) ----------

    @Test
    @DisplayName("IN_FULFILLMENT -> receive -> RECEIVED")
    void receive_fromInFulfillment() {
        SalesOrder so = inFulfillment();
        so.receive("EMP-staff", NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.RECEIVED);
        assertThat(so.receivedBy()).isEqualTo("EMP-staff");
        assertThat(so.receivedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("SUBMITTED 에서 receive 하면 NOT_RECEIVABLE")
    void receive_fromSubmitted_fails() {
        SalesOrder so = submitted();
        assertViolation(() -> so.receive("EMP-staff", NOW), Violation.NOT_RECEIVABLE);
    }

    // ---------- 수정(updateContents) ----------

    @Test
    @DisplayName("REQUESTED 에서는 내용 수정 가능")
    void update_fromRequested() {
        SalesOrder so = requested();
        so.updateContents(SalesOrderPriority.URGENT, "수정 메모", null);
        assertThat(so.priority()).isEqualTo(SalesOrderPriority.URGENT);
        assertThat(so.note()).isEqualTo("수정 메모");
    }

    @Test
    @DisplayName("SUBMITTED 에서는 수정 불가(NOT_EDITABLE)")
    void update_fromSubmitted_fails() {
        SalesOrder so = submitted();
        assertViolation(() -> so.updateContents(SalesOrderPriority.URGENT, "x", null), Violation.NOT_EDITABLE);
    }

    // ---------- 정상 흐름 ----------

    @Test
    @DisplayName("정상 흐름: REQUESTED -> SUBMITTED -> IN_FULFILLMENT -> RECEIVED")
    void happyPath() {
        SalesOrder so = requested();
        so.submit(NOW);
        so.approveByHq("EMP-hq", NOW);
        so.receive("EMP-staff", NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.RECEIVED);
    }

    // ---------- 헬퍼 ----------

    private void assertViolation(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Violation expected) {
        assertThatThrownBy(call)
                .isInstanceOf(SalesOrderStateException.class)
                .extracting(e -> ((SalesOrderStateException) e).getViolation())
                .isEqualTo(expected);
    }
}
