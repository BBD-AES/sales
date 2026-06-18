package com.bbd.sales.application.command;

import java.util.List;

public record UpdateCustomerOrderCommand(
        String coNumber, String note, List<CustomerOrderLineCommand> lines
) {
    public boolean hasLineReplacement() {
        return lines != null;
    }
}
