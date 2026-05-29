package com.bbd.sales.domain;

/**
 * 도메인 상태 전이 규칙 위반 예외.
 *
 * 핵심: 이 예외는 HttpStatus 같은 웹/프레임워크 개념을 전혀 모른다.
 * "REQUESTED 가 아니라서 승인 불가" 같은 '업무 규칙' 만 표현한다.
 * HTTP 상태 코드로의 변환은 web 어댑터(GlobalExceptionHandler)가 책임진다.
 * => 도메인을 순수하게 유지하면서도 기존 ProblemDetail 응답 포맷을 재사용할 수 있다.
 */
public class SalesOrderStateException extends RuntimeException {

    public enum Violation {
        NOT_EDITABLE,            // REQUESTED 가 아니라서 수정 불가
        NOT_CANCELABLE,          // REQUESTED 가 아니라서 취소 불가
        NOT_DECIDABLE,           // REQUESTED 가 아니라서 승인/반려 불가
        NOT_RECEIVABLE,          // APPROVED 가 아니라서 수령 불가
        REJECT_REASON_REQUIRED   // 반려 사유 누락
    }

    private final Violation violation;

    public SalesOrderStateException(Violation violation) {
        super("SalesOrder 상태 규칙 위반: " + violation);
        this.violation = violation;
    }

    public Violation getViolation() {
        return violation;
    }
}
