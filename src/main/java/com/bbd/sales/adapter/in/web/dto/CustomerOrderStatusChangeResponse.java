package com.bbd.sales.adapter.in.web.dto;

import com.bbd.sales.domain.CustomerOrderStatus;

import java.time.LocalDateTime;

public record CustomerOrderStatusChangeResponse(
        String coNumber, CustomerOrderStatus status, String actor, LocalDateTime changedAt
) {
}
