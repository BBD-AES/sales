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
import com.bbd.sales.domain.SalesOrderStateException;
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
// 쓰기 유스케이스(create/update/cancel/approve/reject/receive)는 클래스 레벨 트랜잭션을 공유한다.
// 조회 메서드만 아래에서 readOnly=true로 좁혀 덮어쓴다.
@Transactional
public class SalesOrderService implements SalesOrderUseCase {

    private final SalesOrderRepository repository;
    private final InventoryPort inventoryPort;
    private final SalesOrderEventPublisher eventPublisher;
    private final CatalogPort catalogPort;
    private final ProcurementPort procurementPort;
    private final ProductionPort productionPort;

    // ============================ 조회 ============================

    @Override
    @Transactional(readOnly = true)
    public SalesOrderPageResult<SalesOrderSummaryResult> search(SearchSalesOrderQuery query) {
        // 지점 사용자는 본인 창고만. 본사/관리자는 필터 그대로.
        // 비-HQ인데 warehouseCode가 없으면(헤더 누락 등) fromScope가 null로 풀려 전체가 노출되므로 차단.
        String fromScope = query.fromWarehouseCode();
        if (!query.currentUser().isHq()) {
            String warehouseCode = query.currentUser().warehouseCode();
            if (warehouseCode == null || warehouseCode.isBlank()) {
                // 정상 경로에선 resolver 가 BRANCH_* 창고코드를 이미 강제(401). 여기선 방어용 + 의미상 '인증헤더 누락'.
                throw new ApiException(ErrorCode.AUTH_HEADER_REQUIRED);
            }
            fromScope = warehouseCode;   // 본인 창고로 강제(전달된 필터 무시)
        }

        LocalDateTime from = query.startDate() != null ? query.startDate().atStartOfDay() : null;
        LocalDateTime to = query.endDate() != null ? query.endDate().atTime(LocalTime.MAX) : null;

        SalesOrderSearchCriteria criteria = new SalesOrderSearchCriteria(
                query.status(), query.priority(), fromScope,
                query.requestedBy(), from, to);

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
        // 출발지(source)는 sales 가 저장하지 않음 -> HQ/충족 단계가 결정.
        String fromName = catalogPort.warehouseName(command.fromWarehouseCode());

        SalesOrder so = SalesOrder.request(
                soNumber,
                command.fromWarehouseCode(), fromName,
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
    public SalesOrderStatusChangeResult submit(String soNumber, CurrentUser currentUser) {
        SalesOrder so = load(soNumber);
        authorizeSubmit(so, currentUser);
        LocalDateTime now = LocalDateTime.now();
        so.submit(now);                          // REQUESTED 검증은 도메인이
        repository.save(so);
        eventPublisher.publishSubmitted(so.soNumber());
        return statusChange(so, currentUser.employeeNumber(), now, null);
    }

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
        requireHqDecidable(so); // 외부 예약 호출 전 상태 선검증(예약 후 도메인 throw 시 고아 예약 방지)

        // 1) 가용분 예약(동기, 부분예약). 음수 방지는 Inventory 의 원자적 차감이 보장.
        List<ReservationResult> results = inventoryPort.reserve(
                so.soNumber(), so.fromWarehouseCode(), toTransferLines(so));

        // 2) 부족분을 품목 조달유형으로 분기: MAKE -> 생산요청, BUY -> 구매요청(PR).
        List<StockTransferLine> toProduce = new ArrayList<>();
        List<StockTransferLine> toPurchase = new ArrayList<>();
        for (ReservationResult r : results) {
            if (r.fullyReserved()) continue;
            SourcingType type = catalogPort.resolveProduct(r.sku()).sourcingType();
            (type == SourcingType.MAKE ? toProduce : toPurchase)
                    .add(new StockTransferLine(r.sku(), r.shortfall()));
        }

        LocalDateTime now = LocalDateTime.now();
        if (toProduce.isEmpty() && toPurchase.isEmpty()) {
            so.approveByHq(currentUser.employeeNumber(), now);   // 전량 가용 -> IN_FULFILLMENT(출고)
            repository.save(so);
            eventPublisher.publishFulfilling(so.soNumber());
        } else {
            so.backorder(currentUser.employeeNumber(), now);     // 부족분 존재 -> BACKORDERED
            repository.save(so);
            if (!toPurchase.isEmpty())
                procurementPort.requestPurchase(so.soNumber(), so.fromWarehouseCode(), toPurchase);
            if (!toProduce.isEmpty())
                productionPort.requestProduction(so.soNumber(), so.fromWarehouseCode(), toProduce);
            eventPublisher.publishBackordered(so.soNumber());
        }
        return statusChange(so, currentUser.employeeNumber(), so.approvedAt(), null);
    }

    @Override
    public SalesOrderStatusChangeResult fulfillBackorder(String soNumber, CurrentUser currentUser) {
        SalesOrder so = load(soNumber);
        authorizeDecision(currentUser);
        requireBackordered(so); // 외부 예약 호출 전 상태 선검증

        // 생산/구매 입고분으로 재예약 시도(동기). 전량 예약되면 충족 진행으로 전환.
        List<ReservationResult> results = inventoryPort.reserve(
                so.soNumber(), so.fromWarehouseCode(), toTransferLines(so));
        boolean allReserved = results.stream().allMatch(ReservationResult::fullyReserved);

        if (allReserved) {
            so.fulfillFromBackorder(LocalDateTime.now());  // BACKORDERED -> IN_FULFILLMENT
            repository.save(so);
            eventPublisher.publishFulfilling(so.soNumber());
        }
        // 아직 입고 전이면 BACKORDERED 유지(멱등 재시도 가능).
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

        // 유일하게 실재고가 움직이는 지점. destination=지점(from); source 는 Inventory 가 soNumber 로 해석.
        // 정합성: Inventory 호출 실패 시 이 트랜잭션이 롤백되어 수령도 취소됨(동기 호출 결속).
        inventoryPort.transferForSalesOrderReceive(
                so.soNumber(), so.fromWarehouseCode(),
                currentUser.employeeNumber(), toTransferLines(so));

        eventPublisher.publishReceived(so.soNumber());
        return statusChange(so, currentUser.employeeNumber(), so.receivedAt(), null);
    }

    // ============================ 내부 헬퍼 ============================

    private SalesOrder load(String soNumber) {
        return repository.findBySoNumber(soNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.SALES_ORDER_NOT_FOUND));
    }

    /** 외부 예약 호출 전 상태 선검증(예약 성공 후 도메인 전이가 throw 되어 고아 예약이 남는 것 방지). */
    private void requireHqDecidable(SalesOrder so) {
        if (!so.status().canHqDecide()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.NOT_DECIDABLE);
        }
    }

    private void requireBackordered(SalesOrder so) {
        if (!so.status().isBackordered()) {
            throw new SalesOrderStateException(SalesOrderStateException.Violation.NOT_FULFILLABLE);
        }
    }

    /** 도메인 라인 -> Inventory 포트 전송 라인(sku/수량). */
    private List<StockTransferLine> toTransferLines(SalesOrder so) {
        return so.lines().stream()
                .map(l -> new StockTransferLine(l.sku(), l.quantity()))
                .toList();
    }

    /*
     * 권한 매트릭스 (의도된 설계 — 규칙 변경 시 이 표도 함께 갱신):
     *   생성        : 본인창고 지점 사용자, ADMIN
     *   조회        : HQ(전체), 지점(본인창고만), ADMIN
     *   수정        : 본인창고 지점 사용자, ADMIN   (REQUESTED 에서만)
     *   제출(->HQ)  : 본인창고 BRANCH_MANAGER, ADMIN  (REQUESTED -> SUBMITTED)
     *   취소        : 본인창고 지점 사용자, ADMIN   (REQUESTED/SUBMITTED 까지)
     *   승인/반려   : HQ_MANAGER, ADMIN            (SUBMITTED 에서)
     *   수령(도착)  : 본인창고 지점 사용자, ADMIN   (IN_FULFILLMENT 에서)
     *
     * 참고: PDF 스펙의 HQ_STAFF '출고(SHIP)처리'는 팀이 SHIPPED 단계를 제거하고
     *       RECEIVED 전이로 통합했기에 별도 ship 권한이 없다(도착확인=지점 몫).
     */
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

    /** 제출(HQ로 올림)은 본인 창고의 지점 관리자(또는 ADMIN). */
    private void authorizeSubmit(SalesOrder so, CurrentUser user) {
        if (user.isAdmin()) return;
        if (!user.isBranchManager()) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_ROLE);
        }
        if (!so.ownedByWarehouse(user.warehouseCode())) {
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
                so.status(), so.priority(),
                so.requestedBy(), so.approvedBy(), so.receivedBy(), so.canceledBy(),
                so.requestedAt(), so.approvedAt(), so.receivedAt(), so.canceledAt(),
                so.totalAmount(), so.note());
    }
}
