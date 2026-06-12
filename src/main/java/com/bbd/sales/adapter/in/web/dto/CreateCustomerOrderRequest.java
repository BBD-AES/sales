package com.bbd.sales.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateCustomerOrderRequest(
        @NotBlank String dealerWarehouseCode,
        @NotBlank String customerName,
        String customerContact,
        String note,
        // @Valid가 리스트 원소 하나하나로 재귀검증(@NotBlank, @Min) 해줌
        @NotEmpty List<@Valid CustomerOrderLineRequest> lines
) {
}
