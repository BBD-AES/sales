package com.bbd.sales.domain;

/** 수주 상태. RECEIVED -> CONFIRMED -> CLOSED, 그 전엔 CANCELED 분기. */
public enum CustomerOrderStatus {
    OPEN, CONFIRMED, CLOSED, CANCELED,
    ;

    public boolean isEditable() {
        return this == OPEN;
    }

    public boolean canConfirm() {
        return this == OPEN;
    }

    public boolean isCancelable() {
        return this == OPEN || this == CONFIRMED;
    }

    public boolean canClose() {
        return this == CONFIRMED;
    }
}
