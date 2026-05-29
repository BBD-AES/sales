package com.bbd.sales.domain;

/**
 * 출고 요청 상태.
 *
 * 헥사고날에서 enum 은 "도메인 언어" 그 자체라 domain 패키지에 둔다.
 * 상태 전이 규칙(어떤 상태에서 무엇이 가능한가)은 SalesOrder 애그리거트가 강제하고,
 * 여기서는 값과 전이 가능 여부 질의만 제공한다.
 *
 * 정상 흐름:  REQUESTED -> APPROVED -> RECEIVED
 * 종료 분기:  REQUESTED -> REJECTED / CANCELED
 */
public enum SalesOrderStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    CANCELED,
    RECEIVED;

    /** 내용 수정/취소가 가능한 상태인가 (요청 단계에서만). */
    public boolean isEditable() {
        return this == REQUESTED;
    }

    /** 승인/반려 결정이 가능한 상태인가. */
    public boolean isDecidable() {
        return this == REQUESTED;
    }

    /** 수령(실재고 이동)이 가능한 상태인가. */
    public boolean isReceivable() {
        return this == APPROVED;
    }
}
