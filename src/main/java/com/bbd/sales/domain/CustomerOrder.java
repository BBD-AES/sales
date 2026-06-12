package com.bbd.sales.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 수주 애그리거트(순수 도메인). 출고 요청과 별개로 고객 수요만 기록
 */
public class CustomerOrder {
    private final String coNumber;
    private final String dealerWarehouseCode; // 수주 받은 딜러(지점)
    private final String dealerName; // 스냅샷
    private final String customerName; // 고객명 스냅샷
    private final String customerContact; // 고객 연락처 스냅샷

    private CustomerOrderStatus status;
    private String note;
    private final List<CustomerOrderLine> lines = new ArrayList<>();

    private String requestedBy, confirmedBy, canceledBy, closedBy;
    private LocalDateTime requestedAt, confirmedAt, canceledAt, closedAt;

    public CustomerOrder(String coNumber, String dealerWarehouseCode, String dealerName, String customerName, String customerContact) {
        this.coNumber = coNumber;
        this.dealerWarehouseCode = dealerWarehouseCode;
        this.dealerName = dealerName;
        this.customerName = customerName;
        this.customerContact = customerContact;
    }

    public static CustomerOrder receive(String coNumber, String dealerWarehouseCode, String dealerName, String customerName, String customerContact,
                                        String note, List<CustomerOrderLine> lines, String requestedBy, LocalDateTime now) {
        if (customerName == null || customerName.isBlank()) throw new IllegalArgumentException("고객명은 필수입니다.");
        validateLines(lines);
        CustomerOrder co = new CustomerOrder(coNumber, dealerWarehouseCode, dealerName, customerName, customerContact);
        co.note = note;
        co.lines.addAll(lines);
        co.status = CustomerOrderStatus.OPEN;
        co.requestedBy = requestedBy;
        co.requestedAt = now;
        return co;
    }

    // 저장된 상태를 그대로 재조립(DB -> 객체 복원). 상태 변경 없이 저장된 값으로만 객체를 재구성하므로 상태 위반 검증은 하지 않음.
    public static CustomerOrder reconstitute(String coNumber, String dealerWarehouseCode, String dealerName, String customerName, String customerContact,
                                             CustomerOrderStatus status, String note, List<CustomerOrderLine> lines, String requestedBy, String confirmedBy, String canceledBy, String closedBy,
                                             LocalDateTime requestedAt, LocalDateTime confirmedAt, LocalDateTime canceledAt, LocalDateTime closedAt) {
        CustomerOrder co = new CustomerOrder(coNumber, dealerWarehouseCode, dealerName, customerName, customerContact);
        co.status = status;
        co.note = note;
        if (lines != null) co.lines.addAll(lines);
        co.requestedBy = requestedBy;
        co.confirmedBy = confirmedBy;
        co.canceledBy = canceledBy;
        co.closedBy = closedBy;
        co.requestedAt = requestedAt;
        co.confirmedAt = confirmedAt;
        co.canceledAt = canceledAt;
        co.closedAt = closedAt;
        return co;
    }

    // 사용자가 note/lines 수정
    public void updateContents(String note, List<CustomerOrderLine> newLines) {
        if (!status.isEditable())
            throw new CustomerOrderStateException(CustomerOrderStateException.Violation.NOT_EDITABLE);
        this.note = note;
        if (newLines != null) {
            validateLines(newLines);
            this.lines.clear();
            this.lines.addAll(newLines);
        }
    }

    public void confirm(String confirmedBy, LocalDateTime now) {
        if (!status.canConfirm())
            throw new CustomerOrderStateException(CustomerOrderStateException.Violation.NOT_CONFIRMABLE);
        this.status = CustomerOrderStatus.CONFIRMED;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = now;
    }

    public void close(String closedBy, LocalDateTime now) {
        if (!status.canClose()) {
            throw new CustomerOrderStateException(CustomerOrderStateException.Violation.NOT_CLOSABLE);
        }
        this.status = CustomerOrderStatus.CLOSED;
        this.closedBy = closedBy;
        this.closedAt = now;
    }

    public void cancel(String canceledBy, LocalDateTime now) {
        if (!status.isCancelable())
            throw new CustomerOrderStateException(CustomerOrderStateException.Violation.NOT_CANCELABLE);
        this.status = CustomerOrderStatus.CANCELED;
        this.canceledBy = canceledBy;
        this.canceledAt = now;
    }

    private static void validateLines(List<CustomerOrderLine> lines) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("수주 라인은 최소 1개 이상이어야 합니다.");
        Set<String> skus = new HashSet<>();
        for (CustomerOrderLine line : lines) {
            if (line == null) throw new IllegalArgumentException("수주 라인은 null일 수 없습니다.");
            if (!skus.add(line.sku())) throw new IllegalArgumentException("중복된 SKU는 허용되지 않습니다." + line.sku());
        }
    }

    public BigDecimal totalAmount() {
        // 초기값 0으로 시작해서 각 라인의 amount()를 더해나감. 라인 없으면 0 반환
        return lines.stream().map(CustomerOrderLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean ownedByWarehouse(String warehouseCode) {
        return dealerWarehouseCode != null && dealerWarehouseCode.equals(warehouseCode);
    }

    public String coNumber() {
        return coNumber;
    }
    public String dealerWarehouseCode() {
        return dealerWarehouseCode;
    }
    public String dealerName() {
        return dealerName;
    }
    public String customerName() {
        return customerName;
    }
    public String customerContact() {
        return customerContact;
    }
    public CustomerOrderStatus status() {
        return status;
    }
    public String note() {
        return note;
    }
    public List<CustomerOrderLine> lines() {
        return Collections.unmodifiableList(lines);
    }
    public String requestedBy() {
        return requestedBy;
    }
    public String confirmedBy() {
        return confirmedBy;
    }
    public String canceledBy() {
        return canceledBy;
    }
    public String closedBy() {
        return closedBy;
    }
    public LocalDateTime requestedAt() {
        return requestedAt;
    }
    public LocalDateTime confirmedAt() {
        return confirmedAt;
    }
    public LocalDateTime canceledAt() {
        return canceledAt;
    }
    public LocalDateTime closedAt() {
        return closedAt;
    }
}


