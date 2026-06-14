package com.bbd.sales.application.port.out;

import com.bbd.sales.domain.CustomerOrder;

import java.util.List;

public record CustomerOrderPage(
        List<CustomerOrder> content, long totalElements, int page, int size
) {
    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
