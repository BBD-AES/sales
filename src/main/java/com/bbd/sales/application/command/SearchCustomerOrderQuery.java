package com.bbd.sales.application.command;

import com.bbd.sales.domain.CustomerOrderStatus;

import java.time.LocalDate;

public record SearchCustomerOrderQuery(
        CustomerOrderStatus status, String dealerWarehouseCode, String customerName, String requestedBy,
        LocalDate startDate, LocalDate endDate, int page, int size
) {
}
