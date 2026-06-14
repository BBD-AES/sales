package com.bbd.sales.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CustomerOrderLineRequest(@NotBlank String sku, @Min(1) int quantity) {
}
