package com.bbd.sales.application.port.out;

import com.bbd.sales.domain.CustomerOrderStatus;

import java.time.LocalDateTime;

public record CustomerOrderSearchCriteria(
        CustomerOrderStatus status, String dealerWarehouseCode, String customerName,
        String requestedBy, LocalDateTime from, LocalDateTime to
) {
}
