package com.bbd.sales.adapter.in.web;

import com.bbd.sales.adapter.in.web.dto.*;
import com.bbd.sales.application.port.in.CustomerOrderUseCase;
import com.bbd.sales.domain.CustomerOrderStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customer-orders")
public class CustomerOrderController {
    private final CustomerOrderUseCase customerOrderUseCase; // service가 자동 주입됨
    private final CustomerOrderWebMapper webMapper;

    @GetMapping
    public SalesOrderPageResponse<CustomerOrderSummaryResponse> search(
            @RequestParam(required = false) CustomerOrderStatus status,
            @RequestParam(required = false, name = "dealer_warehouse_code") String dealerWarehouseCode,
            @RequestParam(required = false, name = "customer_name") String customerName,
            @RequestParam(required = false, name = "requested_by") String requestedBy,
            @RequestParam(required = false, name = "start_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false, name = "end_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return webMapper.toSummaryPageResponse(
                customerOrderUseCase.search(webMapper.toSearchQuery(status, dealerWarehouseCode, customerName, requestedBy, startDate, endDate, page, size))
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerOrderDetailResponse create(@Valid @RequestBody CreateCustomerOrderRequest request) {
        return webMapper.toDetailResponse(customerOrderUseCase.create(webMapper.toCreateCommand(request)));
    }

    @GetMapping("/{coNumber}")
    public CustomerOrderDetailResponse get(@PathVariable String coNumber) {
        return webMapper.toDetailResponse(customerOrderUseCase.get(coNumber));
    }

    @PutMapping("/{coNumber}")
    public CustomerOrderDetailResponse update(@PathVariable String coNumber, @Valid @RequestBody UpdateCustomerOrderRequest request) {
        return webMapper.toDetailResponse(customerOrderUseCase.update(webMapper.toUpdateCommand(coNumber, request)));
    }

    @PatchMapping("/{coNumber}/confirm")
    public CustomerOrderStatusChangeResponse confirm(@PathVariable String coNumber) {
        return webMapper.toStatusChangeResponse(customerOrderUseCase.confirm(coNumber));
    }

    @PatchMapping("/{coNumber}/cancel")
    public CustomerOrderStatusChangeResponse cancel(@PathVariable String coNumber) {
        return webMapper.toStatusChangeResponse(customerOrderUseCase.cancel(coNumber));
    }

    @PatchMapping("/{coNumber}/close")
    public CustomerOrderStatusChangeResponse close(@PathVariable String coNumber) {
        return webMapper.toStatusChangeResponse(customerOrderUseCase.close(coNumber));
    }
}
