package com.bbd.sales.domain;

/**
 * 수주 상태 전이 위반(순수 도메인 예외). 경계에서 ErrorCode로 번역
 */
public class CustomOrderStateException extends RuntimeException {
    public enum Violation {
        NOT_EDITABLE, NOT_CONFIRMABLE, NOT_CANCELABLE, NOT_CLOSABLE
    }

    private final Violation violation;

    public CustomOrderStateException(Violation violation) {
        super("CustomerOrder 상태 규칙 위반: " + violation);
        this.violation = violation;
    }

    public Violation violation() {
        return violation;
    }
}
