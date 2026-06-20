package com.bbd.sales.global.error;

import com.bbd.sales.domain.CustomerOrderStateException;
import com.bbd.sales.domain.SalesOrderStateException;
import com.bbd.sales.global.error.dto.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
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
            case NOT_WITHDRAWABLE -> ErrorCode.SALES_ORDER_NOT_WITHDRAWABLE;
            case REJECT_REASON_REQUIRED -> ErrorCode.SALES_ORDER_REJECT_REASON_REQUIRED;
        };
        ApiException mapped = new ApiException(code);
        return ResponseEntity.status(mapped.getStatusCode()).body(mapped.getBody());
    }

    @ExceptionHandler(CustomerOrderStateException.class)
    public ResponseEntity<ProblemDetail> handleCustomerOrderState(CustomerOrderStateException e) {
        ErrorCode code = switch (e.violation()) {
            case NOT_EDITABLE -> ErrorCode.CUSTOMER_ORDER_NOT_EDITABLE;
            case NOT_CONFIRMABLE -> ErrorCode.CUSTOMER_ORDER_NOT_CONFIRMABLE;
            case NOT_CANCELABLE -> ErrorCode.CUSTOMER_ORDER_NOT_CANCELABLE;
            case NOT_CLOSABLE -> ErrorCode.CUSTOMER_ORDER_NOT_CLOSABLE;
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

    /** @Validated 파라미터 제약(@NotBlank 등) 위반 → 400 (미처리 시 500으로 새는 것 방지). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException e) {
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        body.setTitle("BAD_REQUEST");
        body.setDetail(e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * #55 P1: 비관락 획득 실패(lock_timeout 초과/데드락 등) → 409 CONFLICT.
     * 동시 처리 충돌이므로 500 대신 '재시도 가능한 충돌'로 노출한다(CannotAcquireLockException 등 하위 포함).
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handlePessimisticLock(PessimisticLockingFailureException e) {
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        body.setTitle("CONFLICT");
        body.setDetail("다른 처리와 동시 충돌로 일시적으로 실패했습니다. 잠시 후 다시 시도해 주세요.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        body.setTitle("BAD_REQUEST");
        body.setDetail("요청 본문 형식 오류(수량은 1 이상 정수여야 합니다.)");
        return ResponseEntity.badRequest().body(body);
    }
}
