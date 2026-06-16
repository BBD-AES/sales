package com.bbd.sales.global.error.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    SALES_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "SO001", "출고 요청을 찾을 수 없습니다."),
    SALES_ORDER_NOT_EDITABLE(HttpStatus.CONFLICT, "SO002", "REQUESTED 상태의 출고 요청만 수정할 수 있습니다."),
    SALES_ORDER_FORBIDDEN_WAREHOUSE(HttpStatus.FORBIDDEN, "SO003", "본인 소속 창고의 출고 요청만 접근할 수 있습니다."),
    SALES_ORDER_NOT_DECIDABLE(HttpStatus.CONFLICT, "SO004", "SUBMITTED 상태의 출고 요청만 승인/반려할 수 있습니다."),
    SALES_ORDER_NOT_RECEIVABLE(HttpStatus.CONFLICT, "SO005", "IN_FULFILLMENT 상태의 출고 요청만 수령할 수 있습니다."),
    SALES_ORDER_REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "SO006", "반려 사유는 필수입니다."),
    SALES_ORDER_FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "SO007", "해당 작업을 수행할 권한이 없습니다."),
    SALES_ORDER_NOT_SUBMITTABLE(HttpStatus.CONFLICT, "SO008", "REQUESTED 상태의 출고 요청만 HQ로 제출할 수 있습니다."),
    SALES_ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT, "SO009", "REQUESTED 또는 SUBMITTED 상태에서만 취소할 수 있습니다."),
    SALES_ORDER_NOT_FULFILLABLE(HttpStatus.CONFLICT, "SO010", "BACKORDERED 상태의 출고 요청만 충족 처리할 수 있습니다."),

    AUTH_HEADER_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH001", "인증 헤더가 필요합니다."),
    AUTH_ROLE_INVALID(HttpStatus.BAD_REQUEST, "AUTH002", "알 수 없는 역할입니다."),

    EVENT_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EVT001", "이벤트 직렬화에 실패했습니다."),

    CUSTOMER_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "CO001", "수주를 찾을 수 없습니다."),
    CUSTOMER_ORDER_NOT_EDITABLE(HttpStatus.CONFLICT, "CO002", "OPEN 상태의 수주만 수정할 수 있습니다."),
    CUSTOMER_ORDER_FORBIDDEN_WAREHOUSE(HttpStatus.FORBIDDEN, "CO003", "본인 소속 지점의 수주만 접근할 수 있습니다."),
    CUSTOMER_ORDER_NOT_CONFIRMABLE(HttpStatus.CONFLICT, "CO004", "OPEN 상태의 수주만 확정할 수 있습니다."),
    CUSTOMER_ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT, "CO005", "OPEN 또는 CONFIRMED 상태에서만 취소할 수 있습니다."),
    CUSTOMER_ORDER_NOT_CLOSABLE(HttpStatus.CONFLICT, "CO006", "CONFIRMED 상태의 수주만 종료할 수 있습니다."),

    ITEM_NOT_ORDERABLE(HttpStatus.BAD_REQUEST, "ITEM001", "주문할 수 없는 상품(SKU)이 포함되어 있습니다.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
