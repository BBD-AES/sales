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
 * SalesOrder 애그리거트 상태전이 단위테스트 (라인레벨 충족추적).
 * 스프링/JPA/DB 없이 순수 객체만으로 업무 규칙을 검증한다.
 */
class SalesOrderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 3, 10, 0);

    // ---------- 픽스처 (단일 라인: SKU-1, qty 3) ----------

    private SalesOrder requested() {
        return SalesOrder.request("SO-2026-0001", "WH-BR-001", "강남지점",
                SalesOrderPriority.NORMAL, "메모",
                List.of(new SalesOrderLine(1, "SKU-1", "볼트", new BigDecimal("100"), 3)),
                "EMP-staff", NOW);
    }

    private SalesOrder submitted() {
        SalesOrder so = requested();
        so.submit(NOW);
        return so;
    }

    /** 전량 확보(STOCK) 확정 -> IN_FULFILLMENT */
    private SalesOrder inFulfillment() {
        SalesOrder so = submitted();
        so.confirmByHq("EMP-hq", NOW, List.of(new LineReservation("SKU-1", 3, FulfillmentSource.STOCK)));
        return so;
    }

    /** 전량 부족(PURCHASE) 확정 -> BACKORDERED */
    private SalesOrder backordered() {
        SalesOrder so = submitted();
        so.confirmByHq("EMP-hq", NOW, List.of(new LineReservation("SKU-1", 0, FulfillmentSource.PURCHASE)));
        return so;
    }

    private SalesOrderLine line(SalesOrder so) {
        return so.lines().get(0);
    }

    // ---------- 생성 ----------

    @Test
    @DisplayName("생성하면 REQUESTED, 라인 미예약(reserved=0, source=null)")
    void request_createsRequested() {
        SalesOrder so = requested();
        assertThat(so.status()).isEqualTo(SalesOrderStatus.REQUESTED);
        assertThat(so.totalAmount()).isEqualByComparingTo("300");
        assertThat(line(so).reservedQuantity()).isZero();
        assertThat(line(so).fulfillmentSource()).isNull();
        assertThat(line(so).fullyReserved()).isFalse();
    }

    @Test
    @DisplayName("라인 없이 생성하면 거부")
    void request_rejectsEmptyLines() {
        assertThatThrownBy(() -> SalesOrder.request("SO-X", "WH-BR-001", "강남지점",
                SalesOrderPriority.NORMAL, null, List.of(), "EMP-staff", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 제출/취소 ----------

    @Test
    @DisplayName("REQUESTED -> submit -> SUBMITTED")
    void submit_fromRequested() {
        SalesOrder so = requested();
        so.submit(NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.SUBMITTED);
    }

    @Test
    @DisplayName("SUBMITTED 에서 다시 submit -> NOT_SUBMITTABLE")
    void submit_fromNonRequested_fails() {
        SalesOrder so = submitted();
        assertViolation(() -> so.submit(NOW), Violation.NOT_SUBMITTABLE);
    }

    @Test
    @DisplayName("REQUESTED/SUBMITTED 에서는 취소 가능")
    void cancel_allowedUntilSubmitted() {
        SalesOrder a = requested();
        a.cancel("EMP-staff", NOW);
        assertThat(a.status()).isEqualTo(SalesOrderStatus.CANCELED);

        SalesOrder b = submitted();
        b.cancel("EMP-mgr", NOW);
        assertThat(b.status()).isEqualTo(SalesOrderStatus.CANCELED);
    }

    @Test
    @DisplayName("IN_FULFILLMENT 부터는 취소 불가(NOT_CANCELABLE)")
    void cancel_afterFulfillment_fails() {
        SalesOrder so = inFulfillment();
        assertViolation(() -> so.cancel("EMP-staff", NOW), Violation.NOT_CANCELABLE);
    }

    // ---------- HQ 확정 (confirmByHq) ----------

    @Test
    @DisplayName("confirmByHq: 전량 확보 -> IN_FULFILLMENT, 라인 full+STOCK, 승인자 기록")
    void confirmByHq_allReserved_inFulfillment() {
        SalesOrder so = submitted();
        so.confirmByHq("EMP-hq", NOW, List.of(new LineReservation("SKU-1", 3, FulfillmentSource.STOCK)));
        assertThat(so.status()).isEqualTo(SalesOrderStatus.IN_FULFILLMENT);
        assertThat(so.approvedBy()).isEqualTo("EMP-hq");
        assertThat(line(so).fullyReserved()).isTrue();
        assertThat(line(so).fulfillmentSource()).isEqualTo(FulfillmentSource.STOCK);
    }

    @Test
    @DisplayName("confirmByHq: 부분 확보 -> BACKORDERED, 부족분 소스 기록")
    void confirmByHq_shortfall_backordered() {
        SalesOrder so = submitted();
        so.confirmByHq("EMP-hq", NOW, List.of(new LineReservation("SKU-1", 1, FulfillmentSource.PRODUCTION)));
        assertThat(so.status()).isEqualTo(SalesOrderStatus.BACKORDERED);
        assertThat(line(so).reservedQuantity()).isEqualTo(1);
        assertThat(line(so).fulfillmentSource()).isEqualTo(FulfillmentSource.PRODUCTION);
    }

    @Test
    @DisplayName("confirmByHq: REQUESTED 에서 -> NOT_DECIDABLE")
    void confirmByHq_fromRequested_fails() {
        SalesOrder so = requested();
        assertViolation(() -> so.confirmByHq("EMP-hq", NOW, List.of(new LineReservation("SKU-1", 3, FulfillmentSource.STOCK))),
                Violation.NOT_DECIDABLE);
    }

    // ---------- 백오더 재충족 (refulfill) ----------

    @Test
    @DisplayName("refulfill: 잔여 전량 확보 -> IN_FULFILLMENT")
    void refulfill_allReserved_inFulfillment() {
        SalesOrder so = backordered();   // reserved=0
        so.refulfill(List.of(new LineReservation("SKU-1", 3, FulfillmentSource.STOCK)), NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.IN_FULFILLMENT);
        assertThat(line(so).fullyReserved()).isTrue();
    }

    @Test
    @DisplayName("refulfill: 여전히 부족하면 BACKORDERED 유지")
    void refulfill_stillShort_staysBackordered() {
        SalesOrder so = backordered();   // reserved=0, qty 3
        so.refulfill(List.of(new LineReservation("SKU-1", 2, FulfillmentSource.PURCHASE)), NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.BACKORDERED);
        assertThat(line(so).reservedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("refulfill: SUBMITTED 에서 -> NOT_FULFILLABLE")
    void refulfill_fromSubmitted_fails() {
        SalesOrder so = submitted();
        assertViolation(() -> so.refulfill(List.of(new LineReservation("SKU-1", 3, FulfillmentSource.STOCK)), NOW),
                Violation.NOT_FULFILLABLE);
    }

    // ---------- 반려 ----------

    @Test
    @DisplayName("reject: SUBMITTED -> REJECTED(사유)")
    void reject_fromSubmitted() {
        SalesOrder so = submitted();
        so.reject("EMP-hq", "예산 초과", NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.REJECTED);
        assertThat(so.rejectedReason()).isEqualTo("예산 초과");
    }

    @Test
    @DisplayName("reject: 사유 공백 -> REJECT_REASON_REQUIRED")
    void reject_blankReason_fails() {
        SalesOrder so = submitted();
        assertViolation(() -> so.reject("EMP-hq", "  ", NOW), Violation.REJECT_REASON_REQUIRED);
    }

    @Test
    @DisplayName("reject: REQUESTED 에서 -> NOT_DECIDABLE")
    void reject_fromRequested_fails() {
        SalesOrder so = requested();
        assertViolation(() -> so.reject("EMP-hq", "사유", NOW), Violation.NOT_DECIDABLE);
    }

    // ---------- 수령 ----------

    @Test
    @DisplayName("receive: IN_FULFILLMENT -> RECEIVED")
    void receive_fromInFulfillment() {
        SalesOrder so = inFulfillment();
        so.receive("EMP-staff", NOW);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.RECEIVED);
    }

    @Test
    @DisplayName("receive: SUBMITTED 에서 -> NOT_RECEIVABLE")
    void receive_fromSubmitted_fails() {
        SalesOrder so = submitted();
        assertViolation(() -> so.receive("EMP-staff", NOW), Violation.NOT_RECEIVABLE);
    }

    // ---------- 수정 ----------

    @Test
    @DisplayName("updateContents: REQUESTED 가능")
    void update_fromRequested() {
        SalesOrder so = requested();
        so.updateContents(SalesOrderPriority.URGENT, "수정", null);
        assertThat(so.priority()).isEqualTo(SalesOrderPriority.URGENT);
    }

    @Test
    @DisplayName("updateContents: SUBMITTED 에서 -> NOT_EDITABLE")
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
        so.confirmByHq("EMP-hq", NOW, List.of(new LineReservation("SKU-1", 3, FulfillmentSource.STOCK)));
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
