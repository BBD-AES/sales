package com.bbd.sales.global.error;

import com.bbd.sales.global.error.dto.ErrorCode;
import lombok.Getter;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

@Getter
public class ApiException extends ErrorResponseException {

    private final ErrorCode errorCode;

    /**
     * Spring Framework 6+ / Spring Boot 3+부터는 RFC 9457 Problem Details 기반의
     * 에러 응답을 공식적으로 지원한다.
     * 커스텀 예외와 Spring 내장 예외의 응답 형식을 ProblemDetail로 통일할 수 있다.
     */
    public ApiException(ErrorCode errorCode) {
        super(errorCode.getStatus(), createBody(errorCode), null);
        this.errorCode = errorCode;
    }

    /**
     * ErrorCode를 표준 ProblemDetail 응답으로 변환한다.
     *
     * - status: HTTP 상태 코드
     * - title: 에러 코드처럼 짧게 식별 가능한 값(현재 규모에서는 ErrorCode의 code 필드를 그대로 사용)
     * - detail: 클라이언트에 보여줄 상세 메시지
     * - 추가적으로 필요한 필드가 있다면 setProperty로 넣어주면 된다. 예를 들어, 에러 발생 시점의 타임스탬프나 요청 ID 등을 넣을 수 있다.
     * ProblemDetail은 Jackson 직렬화 시 setProperty로 넣은 값을 최상위 JSON 필드로
     * 펼쳐주므로, 기존 ErrorResponse DTO의 code 필드도 유지하면서 표준 포맷을 쓸 수 있다.
     */
    private static ProblemDetail createBody(ErrorCode errorCode) {
        ProblemDetail body = ProblemDetail.forStatus(errorCode.getStatus());
        body.setTitle(errorCode.getCode());
        body.setDetail(errorCode.getMessage());
        return body;
    }
}