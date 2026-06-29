package com.bbd.sales.application.service;

import com.bbd.sales.application.command.CreateSalesOrderCommand;
import com.bbd.sales.application.command.ReserveLineCommand;
import com.bbd.sales.application.command.SalesOrderLineCommand;
import com.bbd.sales.application.command.SearchSalesOrderQuery;
import com.bbd.sales.application.command.UpdateSalesOrderCommand;
import com.bbd.sales.application.port.in.SalesOrderUseCase;
import com.bbd.sales.application.port.out.*;
import com.bbd.sales.application.result.*;
import com.bbd.sales.domain.*;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import com.bbd.sales.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;

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
    private final ProcurementPort procurementPort;
    private final ItemPort itemPort;
    private final WarehousePort warehousePort;
    private final CurrentUserProvider currentUserProvider;
    private final IdempotencyGuard idempotencyGuard; // 생성 멱등(Idempotency-Key)

    // ============================ 조회 ============================

    /** 사용자 권한에 맞는 조회 스코프와 검색조건을 조립해 SO 목록을 페이징 조회함 */
    @Override
    @Transactional(readOnly = true)
    public SalesOrderPageResult<SalesOrderSummaryResult> search(SearchSalesOrderQuery query) {
        CurrentUser user = currentUserProvider.current();
        // 본사/관리자: 전달된 창고 코드 필터 그대로. 지점: 본인 창고로 강제(이름축), 전달된 필터는 무시.
        String codeFilter; // HQ 선택 필터(코드축)
        String nameScope; // 지점 강제 스코핑(이름축)
        if (user.isHq()) {
            codeFilter = query.toWarehouseCode();
            nameScope = null;
        } else {
            String warehouseName = user.warehouseName();
            if (warehouseName == null || warehouseName.isBlank()) {
                // 정상 경로에선 resolver 가 BRANCH의 tenancyName을 항상 채움
                throw new ApiException(ErrorCode.AUTH_HEADER_REQUIRED);
            }
            codeFilter = null; // 지점이 넘긴 코드 필터 무시 -> 타지점 열람 차단
            nameScope = warehouseName;
        }

        LocalDateTime from = query.startDate() != null ? query.startDate().atStartOfDay() : null;
        LocalDateTime to = query.endDate() != null ? query.endDate().atTime(LocalTime.MAX) : null;

        // 영속성 검색 필터
        SalesOrderSearchCriteria criteria = new SalesOrderSearchCriteria(
                query.status(), query.priority(), codeFilter,
                nameScope,
                query.requestedBy(), query.receivedBy(), from, to);

        // 페이징 결과 객체
        SalesOrderPage page = repository.search(criteria, query.page(), query.size());
        List<SalesOrderSummaryResult> items = page.content().stream().map(this::toSummary).toList();
        PaginationResult pagination = new PaginationResult(
                page.page(), page.size(), page.totalElements(), page.totalPages());
        return new SalesOrderPageResult<>(items, pagination);
    }

    /** SO 단건을 조회하고, 지점 사용자는 본인 창고 주문인지 검증한 뒤 상세 결과로 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public SalesOrderResult get(String soNumber) {
        SalesOrder so = load(soNumber);
        authorizeRead(so, currentUserProvider.current()); // 지점=본인창고만(이름기준), HQ/ADMIN=전체.
        return toResult(so);
    }

    /** 상태별 주문 수와 백오더 통계를 집계해 대시보드용 결과를 만든다. */
    @Override
    @Transactional(readOnly = true)
    public SalesOrderStatsResult stats() {
        CurrentUser user = currentUserProvider.current();
        // 지점=본인 창고이름으로 스코프(search 와 동일 규칙), HQ/ADMIN=전체(null).
        String scope;
        if (user.isHq()) {
            scope = null;
        } else {
            scope = user.warehouseName();
            if (scope == null || scope.isBlank()) {
                throw new ApiException(ErrorCode.AUTH_HEADER_REQUIRED);
            }
        }
        Map<SalesOrderStatus, Long> byStatus = repository.countByStatus(scope);

        List<SalesOrder> backordered = repository.findAllByStatus(SalesOrderStatus.BACKORDERED, scope);
        LocalDateTime now = LocalDateTime.now();
        // 대기일 = 요청 후 경과. 음수 방지.
        LongSummaryStatistics waits = backordered.stream()
                .mapToLong(o -> Math.max(0, Duration.between(o.requestedAt(), now).toDays()))
                .summaryStatistics();
        // 백오더 라인 sku별 집계 → 빈도 상위 5
        Map<String, long[]> agg = new LinkedHashMap<>(); // sku -> [lineCount, qtySum]
        Map<String, String> names = new HashMap<>();
        backordered.stream().flatMap(o -> o.lines().stream()).forEach(l -> {
            names.putIfAbsent(l.sku(), l.nameSnapshot());
            long[] a = agg.computeIfAbsent(l.sku(), k -> new long[2]);
            a[0]++;
            a[1] += l.quantity();
        });
        List<SalesOrderStatsResult.TopSku> topSkus = agg.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, long[]>>comparingLong(e -> e.getValue()[0]).reversed()
                        .thenComparing(Map.Entry::getKey)) // 동률 시 sku 2차키로 결정성 보장
                .limit(5)
                .map(e -> new SalesOrderStatsResult.TopSku(e.getKey(), names.get(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .toList();
        SalesOrderStatsResult.BackorderStats backorder = new SalesOrderStatsResult.BackorderStats(
                backordered.size(),
                backordered.isEmpty() ? 0.0 : waits.getAverage(),
                backordered.isEmpty() ? 0L : waits.getMax(),
                topSkus);
        return new SalesOrderStatsResult(byStatus, backorder);
    }

    // ============================ 생성/수정 ============================

    /** 멱등키/창고소유권/품목 스냅샷을 검증한 뒤 신규 SO를 REQUESTED 상태로 생성함 */
    @Override
    public SalesOrderResult create(CreateSalesOrderCommand command) {
        CurrentUser user = currentUserProvider.current();
        // 멱등 표준: 생성 성공 후 키 기록. 동시 같은 키면 UNIQUE 충돌 → 409(IDEM001) → @Transactional 롤백. DB UNIQUE 가 정확성 최종 보루.
        idempotencyGuard.ensureFirst(IdempotencyGuard.SO_CREATE, user.employeeNumber(), command.idempotencyKey());

        // 창고명 스냅샷
        String toName = warehousePort.warehouseName(command.toWarehouseCode());
        // 창고명을 조회하지 못하면 생성하지 않는다.
        if (toName == null || toName.equals(command.toWarehouseCode())) {
            throw new ApiException(ErrorCode.WAREHOUSE_NAME_UNAVAILABLE, command.toWarehouseCode());
        }

        // 지점 사용자는 본인 창고 앞으로만 SO를 생성할 수 있다. ADMIN은 예외로 허용한다.
        if (!user.isAdmin() && !(user.isBranchUser() && toName.equals(user.warehouseName()))) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_WAREHOUSE);
        }

        List<SalesOrderLine> lines = toDomainLines(command.lines());
        String soNumber = repository.nextSoNumber();

        SalesOrder so = SalesOrder.request(
                soNumber,
                command.toWarehouseCode(), toName,
                command.priority(), command.note(), command.customerOrderNumber(), lines,
                user.employeeNumber(), LocalDateTime.now());

        SalesOrder saved = repository.save(so);
        // 생성 성공 후 멱등키를 기록해 같은 요청이 중복 생성되지 않도록 한다.
        idempotencyGuard.record(IdempotencyGuard.SO_CREATE, user.employeeNumber(), command.idempotencyKey(), saved.soNumber());
        // 생성된 SO에 대해 요청 지점 알림 이벤트를 발행한다. 알림 실패는 주문 생성을 막지 않는다.
        eventPublisher.publishRequested(saved.soNumber(), saved.toWarehouseName());
        return toResult(saved);
    }

    /** 본인 창고 SO인지 확인하고 REQUESTED 상태에서 우선순위/메모/라인을 수정한다. */
    @Override
    public SalesOrderResult update(UpdateSalesOrderCommand command) {
        SalesOrder so = load(command.soNumber());
        authorizeOwnerWrite(so, currentUserProvider.current());

        List<SalesOrderLine> newLines = command.hasLineReplacement()
                ? toDomainLines(command.lines())
                : null;

        so.updateContents(command.priority(), command.note(), newLines); // REQUESTED 검증은 도메인이
        SalesOrder saved = repository.save(so);
        return toResult(saved);
    }

    // ============================ 상태 전이 ============================


    /** 지점 관리자가 REQUESTED SO를 HQ 검토 상태인 SUBMITTED로 제출함 */
    @Override
    public SalesOrderStatusChangeResult submit(String soNumber) {
        SalesOrder so = load(soNumber);
        authorizeSubmit(so, currentUserProvider.current());
        LocalDateTime now = LocalDateTime.now();
        so.submit(now);                          // REQUESTED 검증은 도메인이
        repository.save(so);
        eventPublisher.publishSubmitted(so.soNumber());
        return statusChange(so, currentUserProvider.current().employeeNumber(), now, null);
    }

    /** SUBMITTED SO를 REQUESTED 상태로 회수하고 기존 예약을 inventory에 반납함 */
    @Override
    public SalesOrderStatusChangeResult withdraw(String soNumber) {
        SalesOrder so = load(soNumber);
        authorizeOwnerWrite(so, currentUserProvider.current());
        LocalDateTime now = LocalDateTime.now();
        so.withdraw(now);
        repository.save(so);
        // SUBMITTED 상태에서 잡아둔 재고 예약을 해제한다.
        // release는 soNumber 기준 멱등 처리되므로, 같은 요청이 다시 들어와도 중복 해제되지 않는다.
        inventoryPort.release(so.soNumber());
        return statusChange(so, currentUserProvider.current().employeeNumber(), now, null);
    }

    /** HQ 예약 화면에서 특정 SKU의 창고별 가용재고를 inventory에서 조회함 */
    @Override @Transactional(readOnly = true)
    public List<WarehouseStock> stockAvailability(String soNumber, String sku) {
        SalesOrder so = load(soNumber);
        authorizeDecision(currentUserProvider.current());   // HQ_MANAGER/ADMIN
        return inventoryPort.availability(sku);
    }

    /** HQ가 선택한 창고에 재고를 예약하고 실제 예약 수량을 SO 라인에 누적함. */
    @Override
    public SalesOrderResult reserveLine(ReserveLineCommand cmd) {
        CurrentUser user = currentUserProvider.current();
        // 외부 inventory 호출을 동반하므로 주문 행 비관락은 잡지 않는다.
        // 예약 중복 방지는 inventory의 Idempotency-Key 처리에 맡긴다.
        SalesOrder so = load(cmd.soNumber());
        authorizeDecision(user);

        // inventory 예약 전에 주문 상태와 SKU가 예약 가능한지 먼저 검증한다.
        so.assertReservable(cmd.sku());

        if (cmd.quantity() <= 0) {
            throw new IllegalArgumentException("예약 수량은 1 이상이어야 합니다: " + cmd.quantity());
        }
        // 필요한 미충족 수량까지만 예약 요청한다.
        int shortfall = so.shortfallFor(cmd.sku());
        if (shortfall <= 0) {
            throw new ApiException(ErrorCode.SALES_ORDER_LINE_FULLY_RESERVED);
        }
        int want = Math.min(cmd.quantity(), shortfall);
        // inventory가 실제로 예약한 수량만 SO 라인에 반영한다.
        ReservationResult rr = inventoryPort.reserveFromWarehouse(
                cmd.idempotencyKey(), so.soNumber(), cmd.sku(), cmd.warehouseCode(), want);

        so.reserveLine(cmd.sku(), rr.reserved());
        repository.save(so);
        return toResult(so);   // 응답에 라인별 reservedQuantity/부족분 → 사람이 보고 또 예약
    }

    /** HQ가 SUBMITTED SO를 승인하고, 예약 충족 여부에 따라 IN_FULFILLMENT 또는 BACKORDERED로 전이한다. */
    @Override
    public SalesOrderStatusChangeResult approve(String soNumber) {
        CurrentUser user = currentUserProvider.current();

        // 비인가 요청이 주문 행 락을 잡지 않도록 권한을 먼저 검사한다.
        authorizeDecision(user);

        // 같은 SO에 대한 동시 승인/재확정을 막기 위해 주문 행을 잠근다.
        repository.lockForUpdate(soNumber);

        SalesOrder so = load(soNumber);

        // 도메인이 상태 전이와 충족 여부 판단을 수행한다.
        so.confirmByHq(user.employeeNumber(), LocalDateTime.now());

        repository.save(so);

        // 부족분이 있으면 procurement에 구매 요청을 보낸다.
        List<ShortfallLine> shortfall = so.shortfallLines();
        if (!shortfall.isEmpty()) {
            procurementPort.requestPurchase(so.soNumber(), so.toWarehouseCode(), shortfall);
        }

        // 백오더 상태면 HQ 알림을 발행한다.
        if (so.status() == SalesOrderStatus.BACKORDERED) {
            eventPublisher.publishBackordered(so.soNumber());
        }

        // 전량 충족 상태면 도착 지점 알림을 발행한다.
        if (so.status() == SalesOrderStatus.IN_FULFILLMENT) {
            eventPublisher.publishInFulfillment(so.soNumber(), so.toWarehouseName());
        }
        return statusChange(so, user.employeeNumber(), so.approvedAt(), null);
    }

    /** BACKORDERED SO의 보충 예약 결과를 다시 확정해 충족되면 IN_FULFILLMENT로 전이시킴 */
    @Override
    public SalesOrderStatusChangeResult fulfillBackorder(String soNumber) {
        CurrentUser user = currentUserProvider.current();
        authorizeDecision(user);
        repository.lockForUpdate(soNumber);
        SalesOrder so = load(soNumber);
        LocalDateTime now = LocalDateTime.now();
        so.refulfill(now);
        repository.save(so);
        if (so.status() == SalesOrderStatus.IN_FULFILLMENT) {
            eventPublisher.publishInFulfillment(so.soNumber(), so.toWarehouseName());
        }
        return statusChange(so, user.employeeNumber(), now, null);
    }

    /** 지점 사용자가 REQUESTED SO를 취소하고 잡혀 있던 예약을 inventory에 반납함 */
    @Override
    public SalesOrderStatusChangeResult cancel(String soNumber) {
        SalesOrder so = load(soNumber);
        authorizeOwnerWrite(so, currentUserProvider.current());
        so.cancel(currentUserProvider.current().employeeNumber(), LocalDateTime.now());
        repository.save(so);
        // 예약반납
        inventoryPort.release(so.soNumber());
        return statusChange(so, currentUserProvider.current().employeeNumber(), so.canceledAt(), null);
    }

    /** HQ가 SUBMITTED SO를 사유와 함께 반려하고 잡혀 있던 예약을 inventory에 반납함 */
    @Override
    public SalesOrderStatusChangeResult reject(String soNumber, String reason) {
        CurrentUser currentUser = currentUserProvider.current();
        SalesOrder so = load(soNumber);
        authorizeDecision(currentUser);
        so.reject(currentUser.employeeNumber(), reason, LocalDateTime.now()); // 사유 필수 검증은 도메인이
        repository.save(so);
        inventoryPort.release(so.soNumber()); // 예약 반납(보상) — 멱등
        return statusChange(so, currentUser.employeeNumber(), so.rejectedAt(), so.rejectedReason());
    }

    /** 지점 사용자가 IN_FULFILLMENT SO를 RECEIVED로 닫고 수령 이벤트를 발행함 */
    @Override
    public SalesOrderStatusChangeResult receive(String soNumber) {
        CurrentUser currentUser = currentUserProvider.current();
        // 소유권 인가는 SO가 필요 → 비잠금 선로드로 먼저 검사(소유 필드는 불변이라 비잠금 읽기로 충분).
        // 비인가(타지점)는 락을 잡기 전에 차단 — 락-증폭 방지(CodeRabbit #55).
        authorizeOwnerWrite(load(soNumber), currentUser);
        repository.lockForUpdate(soNumber); // #55 P1: 인가 통과 후 행 잠금(수령=출고이벤트 발행 동시 진입 직렬화 — on-hand 이중차감 창 축소)
        SalesOrder so = load(soNumber);     // 잠긴 최신 행으로 재적재 — 전이 검증/저장은 이 인스턴스로(스테일 이중적용 방지)

        so.receive(currentUser.employeeNumber(), LocalDateTime.now()); // APPROVED 검증은 도메인이
        repository.save(so);

        // 출고(예약분 차감)는 이벤트로: sales.order.received 발행 → inventory가 구독해 해당 soNumber의 예약분을 issue(출처 OUT)하고
        // 도착 지점(toWarehouseCode)에 입고(IN) 적재한다. toWarehouseCode 를 함께 실어야 목적지 지점 재고가 증가한다.
        // 트랜잭셔널 아웃박스(SO 저장과 같은 커밋). 예약은 approve 때 이미 동기 확정돼 오버셀 위험 없음 → 출고는 비동기 안전.
        // (동기 issue REST 경로 inventoryPort.transferForSalesOrderReceive 도 존재하나, 표준 receive는 이벤트만 사용 — 이중차감 방지.)
        eventPublisher.publishReceived(so.soNumber(), so.toWarehouseCode());

        return statusChange(so, currentUser.employeeNumber(), so.receivedAt(), null);
    }

    // ============================ 내부 헬퍼 ============================

    /** soNumber로 SO를 조회하고 없으면 NOT_FOUND를 던짐 */
    private SalesOrder load(String soNumber) {
        return repository.findBySoNumber(soNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.SALES_ORDER_NOT_FOUND));
    }

    /** 조회 권한을 검사해 HQ는 전체, 지점은 본인 창고 SO만 허용한다. */
    private void authorizeRead(SalesOrder so, CurrentUser user) {
        if (user.isHq()) return;   // 본사(ADMIN/HQ_*)는 전 창고 조회 — 창고 비스코핑
        if (!so.ownedByWarehouseName(user.warehouseName())) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_WAREHOUSE);
        }
    }

    /** 수정/취소/수령 같은 지점 쓰기 작업이 본인 창고 SO에만 가능하도록 막는다. */
    private void authorizeOwnerWrite(SalesOrder so, CurrentUser user) {
        if (user.isAdmin()) return;
        if (!(user.isBranchUser() && so.ownedByWarehouseName(user.warehouseName()))) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_WAREHOUSE);
        }
    }

    /** (본사에) 제출하는 작업이 본인 창고 SO에 대해서만 가능하도록 소유권을 검사한다.*/
    private void authorizeSubmit(SalesOrder so, CurrentUser user) {
        if (user.isAdmin()) return;
        if (!so.ownedByWarehouseName(user.warehouseName())) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_WAREHOUSE);
        }
    }

    /** HQ 승인/반려/예약 같은 결정 작업 권한이 있는지 서비스 계층에서 한 번 더 검사한다. */
    private void authorizeDecision(CurrentUser user) {
        // 방어적 인가(서비스 경계): @RequireRole(컨트롤러)가 1차 게이트지만, 비-웹 진입점 대비 서비스도 자체 강제.
        if (!user.canDecide()) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_ROLE);
        }
    }

    /** 요청 라인의 SKU를 item에서 조회해 이름/단가/조달유형 스냅샷을 채운 도메인 라인으로 변환한다. */
    private List<SalesOrderLine> toDomainLines(List<SalesOrderLineCommand> lineCommands) {
        List<SalesOrderLine> lines = new ArrayList<>();
        List<String> inactive = new ArrayList<>();
        int lineNo = 1;
        for (SalesOrderLineCommand lc : lineCommands) {
            ProductSnapshot p = itemPort.resolveProduct(lc.sku());
            if (!p.active()) {
                inactive.add(p.sku());
                continue;
            }
            // 사용자가 입력한 순서대로 라인 번호 저장.
            lines.add(new SalesOrderLine(lineNo++, p.sku(), p.name(), p.unitPrice(), p.sourcingType(), lc.quantity()));
        }
        if (!inactive.isEmpty()) {
            throw new ApiException(ErrorCode.ITEM_NOT_ORDERABLE, "주문 불가(비활성) SKU: " + String.join(", ", inactive));
        }
        return lines;
    }

    /** 상태전이 응답에 필요한 주문번호/상태/처리자/시각/사유를 묶어 변환한다. */
    private SalesOrderStatusChangeResult statusChange(SalesOrder so, String actor, LocalDateTime at, String reason) {
        return new SalesOrderStatusChangeResult(so.soNumber(), so.status(), actor, at, reason);
    }

    /** SO 도메인 객체 상세 응답용 application result로 변환한다. */
    private SalesOrderResult toResult(SalesOrder so) {
        List<SalesOrderLineResult> lines = so.lines().stream()
                .map(l -> new SalesOrderLineResult(
                        l.lineNo(), l.sku(), l.nameSnapshot(), l.unitPriceSnapshot(), l.sourcingType(),
                        l.quantity(), l.reservedQuantity(), l.fulfillmentSource()))
                .toList();
        return new SalesOrderResult(
                so.soNumber(),
                so.toWarehouseCode(), so.toWarehouseName(),
                so.status(), so.priority(),
                so.requestedBy(), so.approvedBy(), so.receivedBy(), so.canceledBy(),
                so.requestedAt(), so.approvedAt(), so.receivedAt(), so.canceledAt(),
                so.rejectedReason(), so.totalAmount(), so.note(), so.customerOrderNumber(),
                lines);
    }

    /** SO 도메인 객체를 목록 응답용 summary result로 변환한다. */
    private SalesOrderSummaryResult toSummary(SalesOrder so) {
        return new SalesOrderSummaryResult(
                so.soNumber(),
                so.toWarehouseCode(), so.toWarehouseName(),
                so.status(), so.priority(),
                so.requestedBy(), so.approvedBy(), so.receivedBy(), so.canceledBy(),
                so.requestedAt(), so.approvedAt(), so.receivedAt(), so.canceledAt(),
                so.totalAmount(), so.itemCount(), so.totalQuantity(), so.note());
    }
}
