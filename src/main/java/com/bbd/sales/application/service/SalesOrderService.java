package com.bbd.sales.application.service;

import com.bbd.sales.application.command.CreateSalesOrderCommand;
import com.bbd.sales.application.command.SalesOrderLineCommand;
import com.bbd.sales.application.command.SearchSalesOrderQuery;
import com.bbd.sales.application.command.UpdateSalesOrderCommand;
import com.bbd.sales.application.port.in.SalesOrderUseCase;
import com.bbd.sales.application.port.out.*;
import com.bbd.sales.application.result.*;
import com.bbd.sales.domain.SalesOrder;
import com.bbd.sales.domain.SalesOrderLine;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import com.bbd.sales.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 유스케이스 구현 = "오케스트레이션 계층".
 *  - 권한(역할/소속창고) 검사  -> 여기(application).
 *  - 상태 전이 업무 규칙        -> 도메인(SalesOrder)에 위임.
 *  - DB/재고/이벤트            -> out 포트로 위임.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SalesOrderService implements SalesOrderUseCase {

    private final SalesOrderRepository repository;
    private final InventoryPort inventoryPort;
    private final SalesOrderEventPublisher eventPublisher;
    private final CatalogPort catalogPort;

    // ============================ 조회 ============================

    @Override
    @Transactional(readOnly = true)
    public SalesOrderPageResult<SalesOrderSummaryResult> search(SearchSalesOrderQuery query) {
        // 지점 사용자는 본인 창고만. 본사/관리자는 필터 그대로.
        String fromScope = query.fromWarehouseCode();
        if (!query.currentUser().isHq()) {
            fromScope = query.currentUser().warehouseCode();
        }

        LocalDateTime from = query.startDate() != null ? query.startDate().atStartOfDay() : null;
        LocalDateTime to = query.endDate() != null ? query.endDate().atTime(LocalTime.MAX) : null;

        SalesOrderSearchCriteria criteria = new SalesOrderSearchCriteria(
                query.status(), query.priority(), fromScope,
                query.toWarehouseCode(), query.requestedBy(), from, to);

        SalesOrderPage page = repository.search(criteria, query.page(), query.size());
        List<SalesOrderSummaryResult> items = page.content().stream().map(this::toSummary).toList();
        PaginationResult pagination = new PaginationResult(
                page.page(), page.size(), page.totalElements(), page.totalPages());
        return new SalesOrderPageResult<>(items, pagination);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderResult get(String soNumber, CurrentUser currentUser) {
        SalesOrder so = load(soNumber);
        authorizeRead(so, currentUser);
        return toResult(so);
    }

    // ============================ 생성/수정 ============================

    @Override
    public SalesOrderResult create(CreateSalesOrderCommand command) {
        CurrentUser user = command.currentUser();
        if (!user.isAdmin() && !command.fromWarehouseCode().equals(user.warehouseCode())) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_WAREHOUSE);
        }

        List<SalesOrderLine> lines = toDomainLines(command.lines());
        String soNumber = repository.nextSoNumber();

        // 창고명 스냅샷: 생성 시점에 한 번만 조회해 박는다(이후 읽기는 원격 호출 0).
        String fromName = catalogPort.warehouseName(command.fromWarehouseCode());
        String toName = catalogPort.warehouseName(command.toWarehouseCode());

        SalesOrder so = SalesOrder.request(
                soNumber,
                command.fromWarehouseCode(), fromName,
                command.toWarehouseCode(), toName,
                command.priority(), command.note(), lines,
                user.employeeNumber(), LocalDateTime.now());

        SalesOrder saved = repository.save(so);
        eventPublisher.publishRequested(saved.soNumber());
        return toResult(saved);
    }

    @Override
    public SalesOrderResult update(UpdateSalesOrderCommand command) {
        SalesOrder so = load(command.soNumber());
        authorizeOwnerWrite(so, command.currentUser());

        List<SalesOrderLine> newLines = command.hasLineReplacement()
                ? toDomainLines(command.lines())
                : null;

        so.updateContents(command.priority(), command.note(), newLines); // REQUESTED 검증은 도메인이
        SalesOrder saved = repository.save(so);
        eventPublisher.publishUpdated(saved.soNumber());
        return toResult(saved);
    }

    // ============================ 상태 전이 ============================

    @Override
    public SalesOrderStatusChangeResult cancel(String soNumber, CurrentUser currentUser) {
        SalesOrder so = load(soNumber);
        authorizeOwnerWrite(so, currentUser);
        so.cancel(currentUser.employeeNumber(), LocalDateTime.now());
        repository.save(so);
        eventPublisher.publishCanceled(so.soNumber());
        return statusChange(so, currentUser.employeeNumber(), so.canceledAt(), null);
    }

    @Override
    public SalesOrderStatusChangeResult approve(String soNumber, CurrentUser currentUser) {
        SalesOrder so = load(soNumber);
        authorizeDecision(currentUser);
        so.approve(currentUser.employeeNumber(), LocalDateTime.now());
        repository.save(so);
        eventPublisher.publishApproved(so.soNumber());
        return statusChange(so, currentUser.employeeNumber(), so.approvedAt(), null);
    }

    @Override
    public SalesOrderStatusChangeResult reject(String soNumber, String reason, CurrentUser currentUser) {
        SalesOrder so = load(soNumber);
        authorizeDecision(currentUser);
        so.reject(currentUser.employeeNumber(), reason, LocalDateTime.now()); // 사유 필수 검증은 도메인이
        repository.save(so);
        eventPublisher.publishRejected(so.soNumber());
        return statusChange(so, currentUser.employeeNumber(), so.rejectedAt(), so.rejectedReason());
    }

    @Override
    public SalesOrderStatusChangeResult receive(String soNumber, CurrentUser currentUser) {
        SalesOrder so = load(soNumber);
        authorizeOwnerWrite(so, currentUser);

        so.receive(currentUser.employeeNumber(), LocalDateTime.now()); // APPROVED 검증은 도메인이
        repository.save(so);

        // 유일하게 실재고가 움직이는 지점. source=HQ(to) -> destination=지점(from).
        // 정합성: Inventory 호출 실패 시 이 트랜잭션이 롤백되어 수령도 취소됨(p.198 호출측 트랜잭션 결속).
        // 단 MSA(DB-per-service)라 진짜 분산 원자성은 Saga/Outbox(도전 B-03)로 보강해야 한다.
        List<StockTransferLine> transferLines = so.lines().stream()
                .map(l -> new StockTransferLine(l.sku(), l.quantity()))
                .toList();
        inventoryPort.transferForSalesOrderReceive(
                so.soNumber(), so.toWarehouseCode(), so.fromWarehouseCode(),
                currentUser.employeeNumber(), transferLines);

        eventPublisher.publishReceived(so.soNumber());
        return statusChange(so, currentUser.employeeNumber(), so.receivedAt(), null);
    }

    // ============================ 내부 헬퍼 ============================

    private SalesOrder load(String soNumber) {
        return repository.findBySoNumber(soNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.SALES_ORDER_NOT_FOUND));
    }

    private void authorizeRead(SalesOrder so, CurrentUser user) {
        if (user.isHq()) return;
        if (!so.ownedByWarehouse(user.warehouseCode())) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_WAREHOUSE);
        }
    }

    private void authorizeOwnerWrite(SalesOrder so, CurrentUser user) {
        if (user.isAdmin()) return;
        if (!(user.isBranchUser() && so.ownedByWarehouse(user.warehouseCode()))) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_WAREHOUSE);
        }
    }

    private void authorizeDecision(CurrentUser user) {
        if (!user.canDecide()) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_ROLE);
        }
    }

    private List<SalesOrderLine> toDomainLines(List<SalesOrderLineCommand> lineCommands) {
        List<SalesOrderLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (SalesOrderLineCommand lc : lineCommands) {
            ProductSnapshot p = catalogPort.resolveProduct(lc.sku());
            lines.add(new SalesOrderLine(lineNo++, p.sku(), p.name(), p.unitPrice(), lc.quantity()));
        }
        return lines;
    }

    private SalesOrderStatusChangeResult statusChange(SalesOrder so, String actor, LocalDateTime at, String reason) {
        return new SalesOrderStatusChangeResult(so.soNumber(), so.status(), actor, at, reason);
    }

    private SalesOrderResult toResult(SalesOrder so) {
        List<SalesOrderLineResult> lines = so.lines().stream()
                .map(l -> new SalesOrderLineResult(
                        l.lineNo(), l.sku(), l.nameSnapshot(), l.unitPriceSnapshot(), l.quantity()))
                .toList();
        return new SalesOrderResult(
                so.soNumber(),
                so.fromWarehouseCode(), so.fromWarehouseName(),
                so.toWarehouseCode(), so.toWarehouseName(),
                so.status(), so.priority(),
                so.requestedBy(), so.approvedBy(), so.receivedBy(), so.canceledBy(),
                so.requestedAt(), so.approvedAt(), so.receivedAt(), so.canceledAt(),
                so.rejectedReason(), so.totalAmount(), so.note(),
                lines);
    }

    private SalesOrderSummaryResult toSummary(SalesOrder so) {
        return new SalesOrderSummaryResult(
                so.soNumber(),
                so.fromWarehouseCode(), so.fromWarehouseName(),
                so.toWarehouseCode(), so.toWarehouseName(),
                so.status(), so.priority(),
                so.requestedBy(), so.approvedBy(), so.receivedBy(), so.canceledBy(),
                so.requestedAt(), so.approvedAt(), so.receivedAt(), so.canceledAt(),
                so.totalAmount(), so.note());
    }
}
