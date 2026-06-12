package com.bbd.sales.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateCustomerOrderRequest(
        String note,
        @Size(min = 1, message = "라인을 교체할 경우 최소 1개 이상이어야 합니다.")
        List<@Valid CustomerOrderLineRequest> lines
) {
}
