package com.bbd.sales.adapter.in.web;

import com.bbd.sales.adapter.in.web.dto.*;
import com.bbd.sales.application.port.in.SalesOrderUseCase;
import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;
import com.bbd.sales.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 인바운드(구동) 어댑터 = REST 진입점.
 *
 * 의존 방향: Controller -> SalesOrderUseCase(in 포트). 구현(SalesOrderService)은 모른다.
 * CurrentUser 는 ArgumentResolver 가 헤더에서 만들어 주입 -> 헤더 파싱 코드가 사라짐.
 * 컨트롤러는 "변환 + 위임"만 하고 업무 규칙은 일절 갖지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sales-orders")
public class SalesOrderController {

    private final SalesOrderUseCase salesOrderUseCase;
    private final SalesOrderWebMapper webMapper;

    @GetMapping
    public SalesOrderPageResponse<SalesOrderSummaryResponse> search(
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) SalesOrderPriority priority,
            @RequestParam(required = false, name = "from_warehouse_code") String fromWarehouseCode,
            @RequestParam(required = false, name = "requested_by") String requestedBy,
            @RequestParam(required = false, name = "start_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false, name = "end_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,   // 기존 0 기본값 버그 -> 20
            CurrentUser currentUser
    ) {
        return webMapper.toSummaryPageResponse(
                salesOrderUseCase.search(webMapper.toSearchQuery(
                        status, priority, fromWarehouseCode, requestedBy,
                        startDate, endDate, page, size, currentUser)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesOrderDetailResponse create(
            @Valid @RequestBody CreateSalesOrderRequest request,
            CurrentUser currentUser
    ) {
        return webMapper.toDetailResponse(
                salesOrderUseCase.create(webMapper.toCreateCommand(request, currentUser)));
    }

    @GetMapping("/{soNumber}")
    public SalesOrderDetailResponse get(@PathVariable String soNumber, CurrentUser currentUser) {
        return webMapper.toDetailResponse(salesOrderUseCase.get(soNumber, currentUser));
    }

    @PutMapping("/{soNumber}")
    public SalesOrderDetailResponse update(
            @PathVariable String soNumber,
            @Valid @RequestBody UpdateSalesOrderRequest request,
            CurrentUser currentUser
    ) {
        return webMapper.toDetailResponse(
                salesOrderUseCase.update(webMapper.toUpdateCommand(soNumber, request, currentUser)));
    }

    @PatchMapping("/{soNumber}/submit")
    public SalesOrderStatusChangeResponse submit(@PathVariable String soNumber, CurrentUser currentUser) {
        return webMapper.toStatusChangeResponse(salesOrderUseCase.submit(soNumber, currentUser));
    }

    @PatchMapping("/{soNumber}/cancel")
    public SalesOrderStatusChangeResponse cancel(@PathVariable String soNumber, CurrentUser currentUser) {
        return webMapper.toStatusChangeResponse(salesOrderUseCase.cancel(soNumber, currentUser));
    }

    @PatchMapping("/{soNumber}/approve")
    public SalesOrderStatusChangeResponse approve(@PathVariable String soNumber, CurrentUser currentUser) {
        return webMapper.toStatusChangeResponse(salesOrderUseCase.approve(soNumber, currentUser));
    }

    @PatchMapping("/{soNumber}/reject")
    public SalesOrderStatusChangeResponse reject(
            @PathVariable String soNumber,
            @RequestBody RejectSalesOrderRequest request,
            CurrentUser currentUser
    ) {
        return webMapper.toStatusChangeResponse(
                salesOrderUseCase.reject(soNumber, request.reason(), currentUser));
    }

    @PatchMapping("/{soNumber}/receive")
    public SalesOrderStatusChangeResponse receive(@PathVariable String soNumber, CurrentUser currentUser) {
        return webMapper.toStatusChangeResponse(salesOrderUseCase.receive(soNumber, currentUser));
    }
}
