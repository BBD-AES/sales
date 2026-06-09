package com.bbd.sales.adapter.in.web;

import com.bbd.sales.adapter.in.web.dto.*;
import com.bbd.sales.application.command.CreateSalesOrderCommand;
import com.bbd.sales.application.command.SalesOrderLineCommand;
import com.bbd.sales.application.command.SearchSalesOrderQuery;
import com.bbd.sales.application.command.UpdateSalesOrderCommand;
import com.bbd.sales.application.result.*;
import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;
import com.bbd.sales.global.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 웹 경계 변환기.
 *  요청 DTO -> application 입력(Command/Query)
 *  application 출력(Result) -> 응답 DTO
 * 이 매퍼 덕분에 application 은 web 타입을, web 은 application 내부 규칙을 서로 모른다.
 */
@Component
public class SalesOrderWebMapper {

    // ---------- 요청 -> 입력 ----------

    public SearchSalesOrderQuery toSearchQuery(
            SalesOrderStatus status, SalesOrderPriority priority,
            String toWarehouseCode, String requestedBy,
            LocalDate startDate, LocalDate endDate,
            int page, int size, CurrentUser currentUser) {
        return new SearchSalesOrderQuery(
                status, priority, toWarehouseCode, requestedBy,
                startDate, endDate, page, size, currentUser);
    }

    public CreateSalesOrderCommand toCreateCommand(CreateSalesOrderRequest req, CurrentUser currentUser) {
        return new CreateSalesOrderCommand(
                req.toWarehouseCode(),
                req.priority(),
                req.note(),
                toLineCommands(req.lines()),
                currentUser);
    }

    public UpdateSalesOrderCommand toUpdateCommand(String soNumber, UpdateSalesOrderRequest req, CurrentUser currentUser) {
        return new UpdateSalesOrderCommand(
                soNumber,
                req.priority(),
                req.note(),
                req.lines() != null ? toLineCommands(req.lines()) : null,
                currentUser);
    }

    private List<SalesOrderLineCommand> toLineCommands(List<SalesOrderLineRequest> lines) {
        return lines.stream()
                .map(l -> new SalesOrderLineCommand(l.sku(), l.quantity()))
                .toList();
    }

    // ---------- 출력 -> 응답 ----------

    public SalesOrderPageResponse<SalesOrderSummaryResponse> toSummaryPageResponse(
            SalesOrderPageResult<SalesOrderSummaryResult> result) {
        List<SalesOrderSummaryResponse> items = result.items().stream()
                .map(this::toSummaryResponse)
                .toList();
        PaginationResult p = result.pagination();
        return new SalesOrderPageResponse<>(
                items,
                new PaginationResponse(p.page(), p.size(), p.totalElements(), p.totalPages()));
    }

    public SalesOrderDetailResponse toDetailResponse(SalesOrderResult r) {
        return new SalesOrderDetailResponse(
                r.soNumber(),
                r.toWarehouseCode(), r.toWarehouseName(),
                r.status(), r.priority(),
                r.requestedBy(), r.approvedBy(), r.receivedBy(), r.canceledBy(),
                r.requestedAt(), r.approvedAt(), r.receivedAt(), r.canceledAt(),
                r.rejectedReason(), r.totalAmount(), r.note(),
                r.lines().stream().map(this::toLineResponse).toList());
    }

    public SalesOrderStatusChangeResponse toStatusChangeResponse(SalesOrderStatusChangeResult r) {
        return new SalesOrderStatusChangeResponse(
                r.soNumber(), r.status(), r.actor(), r.changedAt(), r.reason());
    }

    private SalesOrderSummaryResponse toSummaryResponse(SalesOrderSummaryResult r) {
        return new SalesOrderSummaryResponse(
                r.soNumber(),
                r.toWarehouseCode(), r.toWarehouseName(),
                r.status(), r.priority(),
                r.requestedBy(), r.approvedBy(), r.receivedBy(), r.canceledBy(),
                r.requestedAt(), r.approvedAt(), r.receivedAt(), r.canceledAt(),
                r.totalAmount(), r.note());
    }

    private SalesOrderLineResponse toLineResponse(SalesOrderLineResult r) {
        return new SalesOrderLineResponse(
                r.lineNo(), r.sku(), r.nameSnapshot(), r.unitPriceSnapshot(), r.quantity(),
                r.reservedQuantity(), r.fulfillmentSource(), r.fromWarehouseCode());
    }
}
