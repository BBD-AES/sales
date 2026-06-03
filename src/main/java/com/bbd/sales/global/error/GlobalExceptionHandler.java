package com.bbd.sales.global.error;

import com.bbd.sales.domain.SalesOrderStateException;
import com.bbd.sales.global.error.dto.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 전역 예외처리. 모든 갈래를 ProblemDetail 로 통일한다.
 *  1) ApiException                : 권한/조회 실패/인증 등(ErrorCode 보유)
 *  2) SalesOrderStateException    : 도메인 상태 규칙 위반 -> 여기서 web 관심사로 번역
 *  3) IllegalArgumentException    : 도메인/입력값 검증 실패 -> 400 (없으면 500 으로 샘)
 *  (@Valid 실패=MethodArgumentNotValidException 은 상위 ResponseEntityExceptionHandler 가 이미 ProblemDetail 로 처리)
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getBody());
    }

    @ExceptionHandler(SalesOrderStateException.class)
    public ResponseEntity<ProblemDetail> handleSalesOrderState(SalesOrderStateException e) {
        ErrorCode code = switch (e.getViolation()) {
            case NOT_EDITABLE -> ErrorCode.SALES_ORDER_NOT_EDITABLE;
            case NOT_SUBMITTABLE -> ErrorCode.SALES_ORDER_NOT_SUBMITTABLE;
            case NOT_CANCELABLE -> ErrorCode.SALES_ORDER_NOT_CANCELABLE;
            case NOT_DECIDABLE -> ErrorCode.SALES_ORDER_NOT_DECIDABLE;
            case NOT_RECEIVABLE -> ErrorCode.SALES_ORDER_NOT_RECEIVABLE;
            case NOT_FULFILLABLE -> ErrorCode.SALES_ORDER_NOT_FULFILLABLE;
            case REJECT_REASON_REQUIRED -> ErrorCode.SALES_ORDER_REJECT_REASON_REQUIRED;
        };
        ApiException mapped = new ApiException(code);
        return ResponseEntity.status(mapped.getStatusCode()).body(mapped.getBody());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException e) {
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        body.setTitle("BAD_REQUEST");
        body.setDetail(e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
