package com.bbd.sales.domain;

import com.bbd.sales.domain.CustomerOrderStateException.Violation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CustomerOrder 애그리거트 상태전이 단위테스트.
 * 스프링/JPA/DB 없이 순수 객체만으로 업무 규칙을 검증한다.
 */
class CustomerOrderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 3, 10, 0);

    // ---------- 픽스처 (단일 라인: SKU-1, 단가 100, qty 3) ----------

    private List<CustomerOrderLine> singleLine() {
        return List.of(new CustomerOrderLine(1, "SKU-1", "볼트", new BigDecimal("100"), 3));
    }

    private List<CustomerOrderLine> duplicateSkuLines() {
        return List.of(
                new CustomerOrderLine(1, "SKU-1", "볼트", new BigDecimal("100"), 3),
                new CustomerOrderLine(2, "SKU-1", "볼트", new BigDecimal("100"), 2)
        );
    }

    private CustomerOrder open() {
        return CustomerOrder.receive("CO-2026-0001", "WH-BR-001", "강남지점",
                "홍길동", "010-1234-5678", "메모",
                singleLine(), "EMP-staff", NOW);
    }

    private CustomerOrder confirmed() {
        CustomerOrder co = open();
        co.confirm("EMP-mgr", NOW);
        return co;
    }

    private CustomerOrder closed() {
        CustomerOrder co = confirmed();
        co.close("EMP-mgr", NOW);
        return co;
    }

    private CustomerOrder canceled() {
        CustomerOrder co = open();
        co.cancel("EMP-staff", NOW);
        return co;
    }

    private CustomerOrderLine line(CustomerOrder co) {
        return co.lines().get(0);
    }

    // ---------- receive: happy path ----------

    @Test
    @DisplayName("receive: 생성하면 OPEN, requestedAt/requestedBy 설정, 라인·스냅샷 보존")
    void receive_createsOpen() {
        CustomerOrder co = open();

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.OPEN);
        assertThat(co.coNumber()).isEqualTo("CO-2026-0001");
        assertThat(co.dealerWarehouseCode()).isEqualTo("WH-BR-001");
        assertThat(co.dealerName()).isEqualTo("강남지점");
        assertThat(co.customerName()).isEqualTo("홍길동");
        assertThat(co.customerContact()).isEqualTo("010-1234-5678");
        assertThat(co.note()).isEqualTo("메모");
        assertThat(co.requestedBy()).isEqualTo("EMP-staff");
        assertThat(co.requestedAt()).isEqualTo(NOW);
        assertThat(co.confirmedAt()).isNull();
        assertThat(co.canceledAt()).isNull();
        assertThat(co.closedAt()).isNull();

        assertThat(co.lines()).hasSize(1);
        assertThat(line(co).sku()).isEqualTo("SKU-1");
        assertThat(line(co).nameSnapshot()).isEqualTo("볼트");
        assertThat(line(co).unitPriceSnapshot()).isEqualByComparingTo("100");
        assertThat(line(co).quantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("receive: lines() 는 불변 뷰 (외부 수정 불가)")
    void receive_linesAreUnmodifiable() {
        CustomerOrder co = open();
        List<CustomerOrderLine> view = co.lines();

        assertThatThrownBy(() -> view.add(
                new CustomerOrderLine(2, "SKU-9", "추가", new BigDecimal("1"), 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------- receive: 검증 실패 ----------

    @Test
    @DisplayName("receive: 고객명이 공백이면 거부")
    void receive_rejectsBlankCustomerName() {
        assertThatThrownBy(() -> CustomerOrder.receive("CO-X", "WH-BR-001", "강남지점",
                "  ", "010-0000-0000", null, singleLine(), "EMP-staff", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("고객명");
    }

    @Test
    @DisplayName("receive: 고객명이 null 이면 거부")
    void receive_rejectsNullCustomerName() {
        assertThatThrownBy(() -> CustomerOrder.receive("CO-X", "WH-BR-001", "강남지점",
                null, "010-0000-0000", null, singleLine(), "EMP-staff", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("고객명");
    }

    @Test
    @DisplayName("receive: 라인이 비어있으면 거부")
    void receive_rejectsEmptyLines() {
        assertThatThrownBy(() -> CustomerOrder.receive("CO-X", "WH-BR-001", "강남지점",
                "홍길동", "010-0000-0000", null, List.of(), "EMP-staff", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최소 1개");
    }

    @Test
    @DisplayName("receive: 라인이 null 이면 거부")
    void receive_rejectsNullLines() {
        assertThatThrownBy(() -> CustomerOrder.receive("CO-X", "WH-BR-001", "강남지점",
                "홍길동", "010-0000-0000", null, null, "EMP-staff", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최소 1개");
    }

    @Test
    @DisplayName("receive: 같은 sku 라인이 중복되면 거부")
    void receive_rejectsDuplicateSkuLines() {
        assertThatThrownBy(() -> CustomerOrder.receive("CO-X", "WH-BR-001", "강남지점",
                "홍길동", "010-0000-0000", null, duplicateSkuLines(), "EMP-staff", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복된 SKU");
    }

    @Test
    @DisplayName("receive: 라인 항목이 null 이면 거부")
    void receive_rejectsNullLineItem() {
        List<CustomerOrderLine> linesWithNull = Arrays.asList(
                new CustomerOrderLine(1, "SKU-1", "볼트", new BigDecimal("100"), 3),
                null);

        assertThatThrownBy(() -> CustomerOrder.receive("CO-X", "WH-BR-001", "강남지점",
                "홍길동", "010-0000-0000", null, linesWithNull, "EMP-staff", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    // ---------- confirm ----------

    @Test
    @DisplayName("confirm: OPEN -> CONFIRMED, 승인자/시각 기록")
    void confirm_fromOpen() {
        CustomerOrder co = open();
        co.confirm("EMP-mgr", NOW);

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.CONFIRMED);
        assertThat(co.confirmedBy()).isEqualTo("EMP-mgr");
        assertThat(co.confirmedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("confirm: CONFIRMED 에서 다시 confirm -> NOT_CONFIRMABLE")
    void confirm_fromConfirmed_fails() {
        CustomerOrder co = confirmed();
        assertViolation(() -> co.confirm("EMP-mgr", NOW), Violation.NOT_CONFIRMABLE);
    }

    @Test
    @DisplayName("confirm: CLOSED 에서 -> NOT_CONFIRMABLE")
    void confirm_fromClosed_fails() {
        CustomerOrder co = closed();
        assertViolation(() -> co.confirm("EMP-mgr", NOW), Violation.NOT_CONFIRMABLE);
    }

    @Test
    @DisplayName("confirm: CANCELED 에서 -> NOT_CONFIRMABLE")
    void confirm_fromCanceled_fails() {
        CustomerOrder co = canceled();
        assertViolation(() -> co.confirm("EMP-mgr", NOW), Violation.NOT_CONFIRMABLE);
    }

    // ---------- close ----------

    @Test
    @DisplayName("close: CONFIRMED -> CLOSED, 종료자/시각 기록")
    void close_fromConfirmed() {
        CustomerOrder co = confirmed();
        co.close("EMP-mgr", NOW);

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.CLOSED);
        assertThat(co.closedBy()).isEqualTo("EMP-mgr");
        assertThat(co.closedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("close: OPEN 에서 -> NOT_CLOSABLE")
    void close_fromOpen_fails() {
        CustomerOrder co = open();
        assertViolation(() -> co.close("EMP-mgr", NOW), Violation.NOT_CLOSABLE);
    }

    @Test
    @DisplayName("close: CLOSED 에서 다시 close -> NOT_CLOSABLE")
    void close_fromClosed_fails() {
        CustomerOrder co = closed();
        assertViolation(() -> co.close("EMP-mgr", NOW), Violation.NOT_CLOSABLE);
    }

    @Test
    @DisplayName("close: CANCELED 에서 -> NOT_CLOSABLE")
    void close_fromCanceled_fails() {
        CustomerOrder co = canceled();
        assertViolation(() -> co.close("EMP-mgr", NOW), Violation.NOT_CLOSABLE);
    }

    // ---------- cancel ----------

    @Test
    @DisplayName("cancel: OPEN -> CANCELED, 취소자/시각 기록")
    void cancel_fromOpen() {
        CustomerOrder co = open();
        co.cancel("EMP-staff", NOW);

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.CANCELED);
        assertThat(co.canceledBy()).isEqualTo("EMP-staff");
        assertThat(co.canceledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("cancel: CONFIRMED -> CANCELED, 취소자/시각 기록")
    void cancel_fromConfirmed() {
        CustomerOrder co = confirmed();
        co.cancel("EMP-mgr", NOW);

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.CANCELED);
        assertThat(co.canceledBy()).isEqualTo("EMP-mgr");
        assertThat(co.canceledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("cancel: CLOSED 에서 -> NOT_CANCELABLE")
    void cancel_fromClosed_fails() {
        CustomerOrder co = closed();
        assertViolation(() -> co.cancel("EMP-staff", NOW), Violation.NOT_CANCELABLE);
    }

    @Test
    @DisplayName("cancel: CANCELED 에서 다시 cancel -> NOT_CANCELABLE")
    void cancel_fromCanceled_fails() {
        CustomerOrder co = canceled();
        assertViolation(() -> co.cancel("EMP-staff", NOW), Violation.NOT_CANCELABLE);
    }

    // ---------- updateContents ----------

    @Test
    @DisplayName("updateContents: OPEN 에서 note/라인 교체 가능")
    void updateContents_fromOpen() {
        CustomerOrder co = open();
        List<CustomerOrderLine> newLines = List.of(
                new CustomerOrderLine(1, "SKU-2", "너트", new BigDecimal("50"), 4));

        co.updateContents("수정메모", newLines);

        assertThat(co.note()).isEqualTo("수정메모");
        assertThat(co.lines()).hasSize(1);
        assertThat(line(co).sku()).isEqualTo("SKU-2");
        assertThat(line(co).quantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("updateContents: newLines 가 null 이면 note 만 갱신하고 라인은 보존")
    void updateContents_nullLines_keepsLines() {
        CustomerOrder co = open();
        co.updateContents("메모만", null);

        assertThat(co.note()).isEqualTo("메모만");
        assertThat(co.lines()).hasSize(1);
        assertThat(line(co).sku()).isEqualTo("SKU-1");
    }

    @Test
    @DisplayName("updateContents: CONFIRMED 에서 -> NOT_EDITABLE")
    void updateContents_fromConfirmed_fails() {
        CustomerOrder co = confirmed();
        assertViolation(() -> co.updateContents("x", null), Violation.NOT_EDITABLE);
    }

    @Test
    @DisplayName("updateContents: CLOSED 에서 -> NOT_EDITABLE")
    void updateContents_fromClosed_fails() {
        CustomerOrder co = closed();
        assertViolation(() -> co.updateContents("x", null), Violation.NOT_EDITABLE);
    }

    @Test
    @DisplayName("updateContents: CANCELED 에서 -> NOT_EDITABLE")
    void updateContents_fromCanceled_fails() {
        CustomerOrder co = canceled();
        assertViolation(() -> co.updateContents("x", null), Violation.NOT_EDITABLE);
    }

    @Test
    @DisplayName("updateContents: 라인 교체 시 같은 sku 라인이 중복되면 거부")
    void updateContents_rejectsDuplicateSkuLines() {
        CustomerOrder co = open();

        assertThatThrownBy(() -> co.updateContents("수정", duplicateSkuLines()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복된 SKU");

        assertThat(co.lines()).hasSize(1);
        assertThat(line(co).sku()).isEqualTo("SKU-1");
    }

    // ---------- totalAmount ----------

    @Test
    @DisplayName("totalAmount: 라인 amount 합")
    void totalAmount_sumsLineAmounts() {
        CustomerOrder co = CustomerOrder.receive("CO-2026-0002", "WH-BR-001", "강남지점",
                "홍길동", "010-1234-5678", null,
                List.of(
                        new CustomerOrderLine(1, "SKU-1", "볼트", new BigDecimal("100"), 3),  // 300
                        new CustomerOrderLine(2, "SKU-2", "너트", new BigDecimal("50"), 4)    // 200
                ),
                "EMP-staff", NOW);

        assertThat(co.totalAmount()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("totalAmount: 단일 라인은 단가 x 수량")
    void totalAmount_singleLine() {
        CustomerOrder co = open();   // 100 x 3
        assertThat(co.totalAmount()).isEqualByComparingTo("300");
    }

    // ---------- ownedByWarehouse ----------

    @Test
    @DisplayName("ownedByWarehouse: 같은 지점 코드면 true, 다르거나 null 이면 false")
    void ownedByWarehouse() {
        CustomerOrder co = open();   // WH-BR-001

        assertThat(co.ownedByWarehouse("WH-BR-001")).isTrue();
        assertThat(co.ownedByWarehouse("WH-BR-999")).isFalse();
        assertThat(co.ownedByWarehouse(null)).isFalse();
    }

    // ---------- 정상 흐름 ----------

    @Test
    @DisplayName("정상 흐름: OPEN -> CONFIRMED -> CLOSED")
    void happyPath() {
        CustomerOrder co = open();
        co.confirm("EMP-mgr", NOW);
        co.close("EMP-mgr", NOW);
        assertThat(co.status()).isEqualTo(CustomerOrderStatus.CLOSED);
    }

    // ---------- 헬퍼 ----------

    private void assertViolation(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Violation expected) {
        assertThatThrownBy(call)
                .isInstanceOf(CustomerOrderStateException.class)
                .extracting(e -> ((CustomerOrderStateException) e).violation())
                .isEqualTo(expected);
    }
}
