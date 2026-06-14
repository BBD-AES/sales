package com.bbd.sales.application.result;

import com.bbd.sales.domain.CustomerOrderStatus;

import java.time.LocalDateTime;

public record CustomerOrderStatusChangeResult(String coNumber, CustomerOrderStatus status, String actor,
                                              LocalDateTime changedAt) {
}
