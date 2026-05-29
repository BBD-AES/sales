package com.bbd.sales.adapter.in.web.dto;

/**
 * 반려 요청 body.
 * reason 의 필수 검증은 일부러 web(@NotBlank)이 아니라 도메인(SalesOrder.reject)에서 한다.
 * "반려엔 사유가 있어야 한다"는 업무 규칙이므로 코어가 소유 -> 어느 진입점이든 동일하게 강제됨.
 */
public record RejectSalesOrderRequest(
        String reason
) {
}
