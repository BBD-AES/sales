package com.bbd.sales.application.command;

import com.bbd.sales.global.security.CurrentUser;

import java.util.List;

public record UpdateCustomerOrderCommand(
        String coNumber, String note, List<CustomerOrderLineCommand> lines, CurrentUser currentUser
) {
    public boolean hasLineReplacement() {
        return lines != null;
    }
}
