package com.bbd.sales.global.error;

import com.bbd.sales.domain.SalesOrderStateException;
import com.bbd.sales.global.error.dto.ErrorCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 전역 예외처리.
 *
 * 두 갈래를 같은 ProblemDetail 포맷으로 통일한다:
 *  1) application 이 던지는 ApiException(권한/조회 실패 등)
 *  2) domain 이 던지는 SalesOrderStateException(상태 규칙 위반)
 *     -> 도메인은 HTTP 를 모르므로, 여기서 web 관심사(ErrorCode/상태코드)로 '번역'한다.
 *        이 한 군데가 도메인 순수성을 지키는 대가.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException e) {
        return ResponseEntity
                .status(e.getStatusCode())
                .body(e.getBody());
    }

    @ExceptionHandler(SalesOrderStateException.class)
    public ResponseEntity<ProblemDetail> handleSalesOrderState(SalesOrderStateException e) {
        ErrorCode code = switch (e.getViolation()) {
            case NOT_EDITABLE, NOT_CANCELABLE -> ErrorCode.SALES_ORDER_REQUESTED_ONLY;
            case NOT_DECIDABLE -> ErrorCode.SALES_ORDER_DECISION_REQUESTED_ONLY;
            case NOT_RECEIVABLE -> ErrorCode.SALES_ORDER_APPROVED_ONLY;
            case REJECT_REASON_REQUIRED -> ErrorCode.SALES_ORDER_REJECT_REASON_REQUIRED;
        };
        ApiException mapped = new ApiException(code); // 기존 ProblemDetail 변환 로직 재사용
        return ResponseEntity
                .status(mapped.getStatusCode())
                .body(mapped.getBody());
    }
}
