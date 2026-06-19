package com.bbd.sales.application.command;

import java.util.List;

public record CreateCustomerOrderCommand(
        String dealerWarehouseCode, String customerName, String customerContact,
        String note, List<CustomerOrderLineCommand> lines
) {
}
