package com.bbd.sales.application.command;

import java.util.List;

public record CreateCustomerOrderCommand(
        String dealerWarehouseCode, String customerName, String customerContact,
        String note, List<CustomerOrderLineCommand> lines,
        String idempotencyKey   // #71: 클라이언트 Idempotency-Key(헤더). null=멱등 미적용(현행 통과).
) {
}
