package com.bbd.sales.adapter.in.web;

import com.bbd.sales.adapter.in.web.dto.*;
import com.bbd.sales.application.command.CreateSalesOrderCommand;
import com.bbd.sales.application.command.SalesOrderLineCommand;
import com.bbd.sales.application.command.SearchSalesOrderQuery;
import com.bbd.sales.application.command.ReserveLineCommand;
import com.bbd.sales.application.command.UpdateSalesOrderCommand;
import com.bbd.sales.application.port.out.WarehouseStock;
import com.bbd.sales.application.result.*;
import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 웹 경계 변환기.
 * 요청 DTO -> application 입력(Command/Query)
 * application 출력(Result) -> 응답 DTO
 * 이 매퍼 덕분에 application 은 web 타입을, web 은 application 내부 규칙을 서로 모른다.
 */
@Component
public class SalesOrderWebMapper {

    // ---------- 요청 -> 입력 ----------

    public SearchSalesOrderQuery toSearchQuery(
            SalesOrderStatus status, SalesOrderPriority priority,
            String toWarehouseCode, String requestedBy, String receivedBy,
            LocalDate startDate, LocalDate endDate,
            int page, int size) {
        return new SearchSalesOrderQuery(
                status, priority, toWarehouseCode, requestedBy, receivedBy,
                startDate, endDate, page, size);
    }

    public CreateSalesOrderCommand toCreateCommand(CreateSalesOrderRequest req, String idempotencyKey) {
        return new CreateSalesOrderCommand(
                req.toWarehouseCode(),
                req.priority(),
                req.note(),
                toLineCommands(req.lines()),
                idempotencyKey);
    }

    public UpdateSalesOrderCommand toUpdateCommand(String soNumber, UpdateSalesOrderRequest req) {
        return new UpdateSalesOrderCommand(
                soNumber,
                req.priority(),
                req.note(),
                req.lines() != null ? toLineCommands(req.lines()) : null);
    }

    public ReserveLineCommand toReserveLineCommand(String soNumber, ReserveLineRequest req, String idempotencyKey) {
        // 멱등 토큰 일원화: 게이트웨이가 강제하는 Idempotency-Key 헤더를 우선 사용.
        // 레거시 클라이언트(헤더 미전송)는 바디 requestId 로 폴백 — 헤더 표준 전환기 호환.
        String token = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : req.requestId();
        return new ReserveLineCommand(soNumber, req.sku(), req.warehouseCode(), req.quantity(), token);
    }

    public List<WarehouseStockResponse> toStockResponses(List<WarehouseStock> stocks) {
        return stocks.stream()
                .map(s -> new WarehouseStockResponse(s.warehouseCode(), s.warehouseName(), s.available()))
                .toList();
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

    public SalesOrderStatsResponse toStatsResponse(SalesOrderStatsResult r) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        r.byStatus().forEach((k, v) -> byStatus.put(k.name(), v));
        SalesOrderStatsResult.BackorderStats b = r.backorder();
        List<SalesOrderStatsResponse.TopSku> tops = b.topSkus().stream()
                .map(t -> new SalesOrderStatsResponse.TopSku(t.sku(), t.name(), t.lineCount(), t.totalQuantity()))
                .toList();
        return new SalesOrderStatsResponse(byStatus,
                new SalesOrderStatsResponse.Backorder(b.count(), b.avgWaitDays(), b.maxWaitDays(), tops));
    }

    private SalesOrderSummaryResponse toSummaryResponse(SalesOrderSummaryResult r) {
        return new SalesOrderSummaryResponse(
                r.soNumber(),
                r.toWarehouseCode(), r.toWarehouseName(),
                r.status(), r.priority(),
                r.requestedBy(), r.approvedBy(), r.receivedBy(), r.canceledBy(),
                r.requestedAt(), r.approvedAt(), r.receivedAt(), r.canceledAt(),
                r.totalAmount(), r.itemCount(), r.totalQuantity(), r.note());
    }

    private SalesOrderLineResponse toLineResponse(SalesOrderLineResult r) {
        return new SalesOrderLineResponse(
                r.lineNo(), r.sku(), r.nameSnapshot(), r.unitPriceSnapshot(), r.quantity(),
                r.reservedQuantity(), r.fulfillmentSource());
    }
}
