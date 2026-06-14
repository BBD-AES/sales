package com.bbd.sales.application.command;

public record CustomerOrderLineCommand(String sku, int quantity) {
}
