package com.bbd.sales.domain;

/**
 * 수주 상태 전이 위반(순수 도메인 예외).
 * 도메인은 Spring/HTTP를 알면 안되므로 Spring web 타입인 ErrorResponseException을 상속받는 커스텀 에러대신 RuntimeException을 상속받음
 */
public class CustomerOrderStateException extends RuntimeException {
    // 무슨 규칙을 어겼는지 도메인 언어로 표현. 핸들러에서 ErrorCode로 번역됨.
    public enum Violation {
        NOT_EDITABLE, NOT_CONFIRMABLE, NOT_CANCELABLE, NOT_CLOSABLE
    }

    private final Violation violation;

    public CustomerOrderStateException(Violation violation) {
        super("CustomerOrder 상태 규칙 위반: " + violation);
        this.violation = violation;
    }

    public Violation violation() {
        return violation;
    }
}
