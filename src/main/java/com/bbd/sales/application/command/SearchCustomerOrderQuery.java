package com.bbd.sales.application.command;

import com.bbd.sales.domain.CustomerOrderStatus;
import com.bbd.sales.global.security.CurrentUser;

import java.time.LocalDate;

public record SearchCustomerOrderQuery(
        CustomerOrderStatus status, String dealerWarehouseCode, String customerName, String requestedBy,
        LocalDate startDate, LocalDate endDate, int page, int size, CurrentUser currentUser
) {
}
