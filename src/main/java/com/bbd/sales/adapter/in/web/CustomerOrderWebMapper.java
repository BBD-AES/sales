package com.bbd.sales.adapter.in.web;

import com.bbd.sales.adapter.in.web.dto.*;
import com.bbd.sales.application.command.CreateCustomerOrderCommand;
import com.bbd.sales.application.command.CustomerOrderLineCommand;
import com.bbd.sales.application.command.SearchCustomerOrderQuery;
import com.bbd.sales.application.command.UpdateCustomerOrderCommand;
import com.bbd.sales.application.result.*;
import com.bbd.sales.domain.CustomerOrderStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class CustomerOrderWebMapper {
    public SearchCustomerOrderQuery toSearchQuery(
            CustomerOrderStatus status, String dealerWarehouseCode, String customerName, String requestedBy,
            LocalDate startDate, LocalDate endDate, int page, int size
    ) {
        return new SearchCustomerOrderQuery(
                status, dealerWarehouseCode, customerName, requestedBy, startDate, endDate, page, size
        );
    }

    public CreateCustomerOrderCommand toCreateCommand(CreateCustomerOrderRequest req, String idempotencyKey) {
        return new CreateCustomerOrderCommand(req.dealerWarehouseCode(), req.customerName(), req.customerContact(), req.note(), toLineCommands(req.lines()), idempotencyKey);
    }

    public UpdateCustomerOrderCommand toUpdateCommand(String coNumber, UpdateCustomerOrderRequest req) {
        return new UpdateCustomerOrderCommand(coNumber, req.note(),
                req.lines() != null ? toLineCommands(req.lines()) : null);
    }

    // Bean validation은 Spring 진입점(컨트롤러)에서만 동작하므로 private 메서드 파라미터에는 넣지 않음.
    // 검증은 컨트롤러에서 끝남
    private List<CustomerOrderLineCommand> toLineCommands(List<CustomerOrderLineRequest> lines) {
        return lines.stream()
                .map(l -> new CustomerOrderLineCommand(l.sku(), l.quantity())).toList();
    }

    public SalesOrderPageResponse<CustomerOrderSummaryResponse> toSummaryPageResponse(
            SalesOrderPageResult<CustomerOrderSummaryResult> result
    ) {
        List<CustomerOrderSummaryResponse> items = result.items().stream().map(this::toSummaryResponse).toList();
        PaginationResult p = result.pagination();
        return new SalesOrderPageResponse<>(items, new PaginationResponse(p.page(), p.size(), p.totalElements(), p.totalPages()));
    }

    public CustomerOrderDetailResponse toDetailResponse(CustomerOrderResult r) {
        return new CustomerOrderDetailResponse(
                r.coNumber(), r.dealerWarehouseCode(), r.customerName(), r.customerContact(), r.status(),
                r.requestedBy(), r.confirmedBy(), r.canceledBy(), r.closedBy(),
                r.requestedAt(), r.confirmedAt(), r.canceledAt(), r.closedAt(),
                r.totalAmount(), r.note(),
                r.lines().stream().map(this::toLineResponse).toList()
        );
    }

    public CustomerOrderStatusChangeResponse toStatusChangeResponse(CustomerOrderStatusChangeResult r) {
        return new CustomerOrderStatusChangeResponse(r.coNumber(), r.status(), r.actor(), r.changedAt());
    }

    private CustomerOrderSummaryResponse toSummaryResponse(CustomerOrderSummaryResult r) {
        return new CustomerOrderSummaryResponse(
                r.coNumber(), r.dealerWarehouseCode(), r.dealerName(), r.customerName(), r.status(),
                r.requestedBy(), r.confirmedBy(), r.canceledBy(), r.closedBy(),
                r.requestedAt(), r.confirmedAt(), r.canceledAt(), r.closedAt(),
                r.totalAmount(), r.note()
        );
    }

    private CustomerOrderLineResponse toLineResponse(CustomerOrderLineResult r) {
        return new CustomerOrderLineResponse(r.lineNo(), r.sku(), r.nameSnapshot(), r.unitPriceSnapshot(), r.quantity());
    }

}
