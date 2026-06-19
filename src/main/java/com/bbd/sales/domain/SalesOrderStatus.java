package com.bbd.sales.domain;

/**
 * 출고 요청(StockTransferRequest) 상태.
 *
 * 헥사고날에서 enum 은 "도메인 언어" 그 자체라 domain 패키지에 둔다.
 * 상태 전이 규칙(어떤 상태에서 무엇이 가능한가)은 SalesOrder 애그리거트가 강제하고,
 * 여기서는 값과 전이 가능 여부 질의만 제공한다.
 *
 * 정상 흐름:  REQUESTED -> SUBMITTED -> (IN_FULFILLMENT | BACKORDERED) -> RECEIVED
 *   - REQUESTED      : 지점 작성자(BRANCH_STAFF)가 생성, 지점 내부 검토 단계
 *   - SUBMITTED      : 지점 관리자(BRANCH_MANAGER)가 HQ로 제출, HQ 결정 대기
 *   - IN_FULFILLMENT : HQ 승인 + 재고 예약 완료, 실물 이동 진행 중
 *   - BACKORDERED    : HQ 승인했으나 재고 부족 -> PO 대기 (전이 연결은 PO 커밋에서)
 *   - RECEIVED       : 지점 수령 완료(종료)
 * 종료 분기:  SUBMITTED -> REJECTED(HQ 반려) / {REQUESTED,SUBMITTED} -> CANCELED(요청자 철회)
 */
public enum SalesOrderStatus {
    REQUESTED,
    SUBMITTED,
    IN_FULFILLMENT,
    BACKORDERED,
    REJECTED,
    CANCELED,
    RECEIVED;

    /** 내용 수정이 가능한 상태인가 (작성 단계에서만). */
    public boolean isEditable() {
        return this == REQUESTED;
    }

    /** HQ로 제출 가능한 상태인가 (BRANCH_MANAGER). */
    public boolean canSubmit() {
        return this == REQUESTED;
    }

    /** 제출 회수 가능한 상태인가 (SUBMITTED -> REQUESTED, HQ 결정 전) */
    public boolean canWithdraw() {
        return this == SUBMITTED;
    }

    /** 요청자 취소가 가능한 상태인가 (HQ 손에 넘어가기 전까지). */
    public boolean isCancelable() {
        return this == REQUESTED || this == SUBMITTED;
    }

    /** HQ 승인/반려 결정이 가능한 상태인가. */
    public boolean canHqDecide() {
        return this == SUBMITTED;
    }

    /** 충족(IN_FULFILLMENT) 진입이 가능한 상태인가. SUBMITTED(HQ 승인) 또는 BACKORDERED(PO 입고 후). */
    public boolean canFulfill() {
        return this == SUBMITTED || this == BACKORDERED;
    }

    /** 백오더(PO 대기) 상태인가. */
    public boolean isBackordered() {
        return this == BACKORDERED;
    }

    /** 수령(실재고 이동)이 가능한 상태인가. */
    public boolean isReceivable() {
        return this == IN_FULFILLMENT;
    }
}
