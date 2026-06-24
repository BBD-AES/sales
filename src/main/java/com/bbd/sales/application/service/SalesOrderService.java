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
    private final IdempotencyGuard idempotencyGuard; // #71: 생성 멱등(Idempotency-Key)

    // ============================ 조회 ============================

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
                // 정상 경로에선 resolver 가 BRANCH의 tenancyName을 항상 채움. 방어용(인증 컨텍스트 불완전(.
                throw new ApiException(ErrorCode.AUTH_HEADER_REQUIRED);
            }
            codeFilter = null; // 지점이 넘긴 코드 필터 무시 -> 타지점 열람 차단
            nameScope = warehouseName;
        }

        LocalDateTime from = query.startDate() != null ? query.startDate().atStartOfDay() : null;
        LocalDateTime to = query.endDate() != null ? query.endDate().atTime(LocalTime.MAX) : null;

        SalesOrderSearchCriteria criteria = new SalesOrderSearchCriteria(
                query.status(), query.priority(), codeFilter,
                nameScope,
                query.requestedBy(), query.receivedBy(), from, to);

        SalesOrderPage page = repository.search(criteria, query.page(), query.size());
        List<SalesOrderSummaryResult> items = page.content().stream().map(this::toSummary).toList();
        PaginationResult pagination = new PaginationResult(
                page.page(), page.size(), page.totalElements(), page.totalPages());
        return new SalesOrderPageResult<>(items, pagination);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderResult get(String soNumber) {
        SalesOrder so = load(soNumber);
        authorizeRead(so, currentUserProvider.current()); // 지점=본인창고만(이름축), HQ/ADMIN=전체.
        return toResult(so);
    }

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
        // 대기일 = 요청 후 경과(별도 백오더 타임스탬프 없어 근사). 음수 방지.
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

    @Override
    public SalesOrderResult create(CreateSalesOrderCommand command) {
        CurrentUser user = currentUserProvider.current();
        // 멱등 표준: 같은 Idempotency-Key 재요청은 409(이미 처리됨) — 원본 응답 캐시·재생 안 함(docs/idempotency-spec.md).
        idempotencyGuard.ensureFirst(IdempotencyGuard.SO_CREATE, user.employeeNumber(), command.idempotencyKey());

        // 창고명 스냅샷: 생성 시점에 한 번 조회(이후 읽기는 원격 호출 0). 출발지(source)는 sales가 저장 안 함.
        String toName = warehousePort.warehouseName(command.toWarehouseCode());
        // 창고명 미해결(코드 폴백=조회 실패)이면 fail-fast: 코드를 이름으로 박제하면 이후 이름축 소유권검사가 영영 깨지고,
        // 이름 기반 인가도 잘못 거부된다(인벤토리 다운 시 전 생성 차단). 코드-as-이름 저장 방지.
        if (toName == null || toName.equals(command.toWarehouseCode())) {
            throw new ApiException(ErrorCode.WAREHOUSE_NAME_UNAVAILABLE, command.toWarehouseCode());
        }

        // 본인 창고 앞으로만 생성(이름축). ADMIN 예외. 역할(BRANCH_*/ADMIN)은 @RequireRole이 커버.
        // 가드를 라인 해석/번호 채번 전에 둬 미인가 요청은 item 호출/SO 번호 소모 없이 조기 차단.
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
        // 멱등 표준: 생성 성공 후 키 기록. 동시 같은 키면 UNIQUE 충돌 → 409(IDEM001) → @Transactional 롤백. DB UNIQUE 가 정확성 최종 보루.
        idempotencyGuard.record(IdempotencyGuard.SO_CREATE, user.employeeNumber(), command.idempotencyKey(), saved.soNumber());
        return toResult(saved);
    }

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


    /** 헥사고날에서 필요성: 포트만 의존 -> 구현이 뭐든 무관*/
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

    @Override
    public SalesOrderStatusChangeResult withdraw(String soNumber) {
        CurrentUser currentUser = currentUserProvider.current();
        SalesOrder so = load(soNumber);
        authorizeOwnerWrite(so, currentUser); // 본인 지점 소유(취소와 동일 가드)
        LocalDateTime now = LocalDateTime.now();
        so.withdraw(now);
        repository.save(so);
        // SUBMITTED 에서 HQ가 잡아둔 예약 반납(신모델=SUBMITTED에서 예약).
        // #55: withdraw/cancel/reject 의 release 는 '멱등 수렴'(같은 soNumber 재release=no-op)이라 reserveLine 과 달리
        //      비관락 없이도 이중효과가 없다 → 이 외부효과 전이들은 의도적으로 lockForUpdate 를 생략한다.
        inventoryPort.release(so.soNumber());
        return statusChange(so, currentUser.employeeNumber(), now, null);
    }

    /** [가용 조회] 예약 화면용. 권한만 보고 inventory 현황을 그대로 전달. */
    @Override @Transactional(readOnly = true)
    public List<WarehouseStock> stockAvailability(String soNumber, String sku) {
        SalesOrder so = load(soNumber);
        authorizeDecision(currentUserProvider.current());   // HQ_MANAGER/ADMIN
        return inventoryPort.availability(sku);
    }

    /** [라인 예약] 사람이 고른 '한 창고'에서 한 번. inventory에 먼저 잡고(실량 받고) → 도메인에 누적. */
    @Override
    public SalesOrderResult reserveLine(ReserveLineCommand cmd) {
        CurrentUser user = currentUserProvider.current();
        // #55: reserveLine 은 의도적으로 비관락을 걸지 않는다.
        //  - reserveFromWarehouse 는 커밋 전 '외부 REST' 호출이라, 락을 잡은 채 호출하면 inventory 지연/행 동안
        //    행 락이 유지돼 같은 SO 의 모든 전이를 봉쇄(가용성/커넥션풀 위험)한다 — 락-중-네트워크IO 안티패턴.
        //  - 게다가 락은 '동시'만 직렬화할 뿐 '같은 requestId 재시도'의 이중계상은 못 막는다(그건 영속 dedup 몫).
        //  → 동시/재시도 이중예약 방어는 inventory requestId 멱등키(영속 dedup)=#55 2순위(멀티창고와 함께)로 처리한다.
        SalesOrder so = load(cmd.soNumber());
        authorizeDecision(user);
        // 0) 외부 예약 호출 전에 도메인 선검증(상태/SKU) → 예약 성공 후 도메인 throw로 inventory 고아 홀드가 남는 것 방지.
        so.assertReservable(cmd.sku());
        if (cmd.quantity() <= 0) {   // 비양수 수량 로컬 차단(@Positive 웹검증 외 서비스 경계 방어) → inventory에 0/음수 미전달
            throw new IllegalArgumentException("예약 수량은 1 이상이어야 합니다: " + cmd.quantity());
        }
        // 1) 요청을 미충족분(shortfall)으로 clamp — 필요수량 초과 예약(inventory 고아 holds) 방지. 이미 충족이면 거부.
        int shortfall = so.shortfallFor(cmd.sku());
        if (shortfall <= 0) {
            throw new ApiException(ErrorCode.SALES_ORDER_LINE_FULLY_RESERVED);
        }
        int want = Math.min(cmd.quantity(), shortfall);
        // 2) inventory 예약(동기, 원자) → 실제 잡힌 양. idempotencyKey=공통 멱등 토큰(inventory dedup 키로 전파).
        ReservationResult rr = inventoryPort.reserveFromWarehouse(
                cmd.idempotencyKey(), so.soNumber(), cmd.sku(), cmd.warehouseCode(), want);
        // 3) 실제 잡힌 양만 도메인 라인에 누적(상태 그대로 SUBMITTED).
        //    (주의) 같은 멱등키 재요청 시 inventory 멱등 반환을 sales가 또 누적하는 이중계상은 inventory 영속 dedup(UNIQUE)로 분리.
        so.reserveLine(cmd.sku(), rr.reserved());
        repository.save(so);
        return toResult(so);   // 응답에 라인별 reservedQuantity/부족분 → 사람이 보고 또 예약
    }

    /** [확정] approve — 자동예약 제거. 이미 잡힌 상태를 확정만. */
    @Override
    public SalesOrderStatusChangeResult approve(String soNumber) {
        CurrentUser user = currentUserProvider.current();
        authorizeDecision(user); // 역할 인가를 락 획득 전에 — 비인가 호출이 행 락을 잡아 정상 전이를 막는 것 방지(CodeRabbit #55)
        repository.lockForUpdate(soNumber); // #55 P1: 확정+구매통지(아웃박스) 동시 진입 직렬화(낙관락 보강, 깔끔한 충돌 의미)
        SalesOrder so = load(soNumber);
        so.confirmByHq(user.employeeNumber(), LocalDateTime.now());   // reserveAndRoute 호출 없음!
        repository.save(so);
        // 부족분 남으면(BACKORDERED) procurement에 통지(기존 이벤트 그대로)
        List<StockTransferLine> shortfall = so.shortfallLines();
        if (!shortfall.isEmpty()) {
            procurementPort.requestPurchase(so.soNumber(), so.toWarehouseCode(), shortfall);
        }
        // 부족분으로 BACKORDERED 가 되면 HQ 자가알림(best-effort, AFTER_COMMIT — 핵심 전이 비차단).
        if (so.status() == SalesOrderStatus.BACKORDERED) {
            eventPublisher.publishBackordered(so.soNumber());
        }
        // 전량 예약돼 IN_FULFILLMENT 가 되면 도착 지점에 '곧 입고' 자가알림(targetRole=지점 창고명, 이름축).
        if (so.status() == SalesOrderStatus.IN_FULFILLMENT) {
            eventPublisher.publishInFulfillment(so.soNumber(), so.toWarehouseName());
        }
        return statusChange(so, user.employeeNumber(), so.approvedAt(), null);
    }

    /** [재확정] fulfill-backorder — 재예약 없음(예약은 reserveLine으로 이미 함). 확정만. */
    @Override
    public SalesOrderStatusChangeResult fulfillBackorder(String soNumber) {
        CurrentUser user = currentUserProvider.current();
        authorizeDecision(user); // 역할 인가를 락 획득 전에(CodeRabbit #55)
        repository.lockForUpdate(soNumber); // #55 P1: 백오더 재확정 동시 진입 직렬화
        SalesOrder so = load(soNumber);
        LocalDateTime now = LocalDateTime.now();
        so.refulfill(now);
        repository.save(so);
        return statusChange(so, user.employeeNumber(), now, null);
    }

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

        // 출고(예약분 차감)는 이벤트로: sales.order.received 발행 → inventory가 구독해 해당 soNumber의 예약분을 issue.
        // 트랜잭셔널 아웃박스(SO 저장과 같은 커밋). 예약은 approve 때 이미 동기 확정돼 오버셀 위험 없음 → 출고는 비동기 안전.
        // (동기 issue REST 경로 inventoryPort.transferForSalesOrderReceive 도 존재하나, 표준 receive는 이벤트만 사용 — 이중차감 방지.)
        eventPublisher.publishReceived(so.soNumber());

        return statusChange(so, currentUser.employeeNumber(), so.receivedAt(), null);
    }

    // ============================ 내부 헬퍼 ============================

    private SalesOrder load(String soNumber) {
        return repository.findBySoNumber(soNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.SALES_ORDER_NOT_FOUND));
    }

    /*
     * 권한 매트릭스 (의도된 설계 — 규칙 변경 시 이 표도 함께 갱신):
     *   생성        : 본인창고 지점 사용자, ADMIN
     *   조회        : HQ(전체), 지점(본인창고만), ADMIN
     *   수정        : 본인창고 지점 사용자, ADMIN   (REQUESTED 에서만)
     *   제출(->HQ)  : 본인창고 BRANCH_MANAGER, ADMIN  (REQUESTED -> SUBMITTED)
     *   취소        : 본인창고 지점 사용자, ADMIN   (REQUESTED 에서만; 제출 후는 withdraw 로 되돌린 뒤)
     *   승인/반려   : HQ_MANAGER, ADMIN            (SUBMITTED 에서)
     *   수령(도착)  : 본인창고 지점 사용자, ADMIN   (IN_FULFILLMENT 에서)
     *
     * 참고: PDF 스펙의 HQ_STAFF '출고(SHIP)처리'는 팀이 SHIPPED 단계를 제거하고
     *       RECEIVED 전이로 통합했기에 별도 ship 권한이 없다(도착확인=지점 몫).
     */
    private void authorizeRead(SalesOrder so, CurrentUser user) {
        if (user.isHq()) return;   // 본사(ADMIN/HQ_*)는 전 창고 조회 — 창고 비스코핑
        if (!so.ownedByWarehouseName(user.warehouseName())) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_WAREHOUSE);
        }
    }

    /** 쓰기(수정/취소/수령): 본인 창고의 지점 사용자(또는 ADMIN), 역할은 @RequireRole 가 커버. */
    private void authorizeOwnerWrite(SalesOrder so, CurrentUser user) {
        if (user.isAdmin()) return;
        if (!(user.isBranchUser() && so.ownedByWarehouseName(user.warehouseName()))) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_WAREHOUSE);
        }
    }

    /** 제출(HQ로 올림)은 본인 창고의 지점 관리자(또는 ADMIN). 역할은 @RequireRole({BRANCH_MANAGER, ADMIN})가 커버. */
    private void authorizeSubmit(SalesOrder so, CurrentUser user) {
        if (user.isAdmin()) return;
        if (!so.ownedByWarehouseName(user.warehouseName())) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_WAREHOUSE);
        }
    }

    private void authorizeDecision(CurrentUser user) {
        // 방어적 인가(서비스 경계): @RequireRole(컨트롤러)가 1차 게이트지만, 비-웹 진입점 대비 서비스도 자체 강제.
        if (!user.canDecide()) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_ROLE);
        }
    }

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
            // 사용자가 입력한 순서대로 라인 번호 저장
            lines.add(new SalesOrderLine(lineNo++, p.sku(), p.name(), p.unitPrice(), lc.quantity()));
        }
        if (!inactive.isEmpty()) {
            throw new ApiException(ErrorCode.ITEM_NOT_ORDERABLE, "주문 불가(비활성) SKU: " + String.join(", ", inactive));
        }
        return lines;
    }

    private SalesOrderStatusChangeResult statusChange(SalesOrder so, String actor, LocalDateTime at, String reason) {
        return new SalesOrderStatusChangeResult(so.soNumber(), so.status(), actor, at, reason);
    }

    private SalesOrderResult toResult(SalesOrder so) {
        List<SalesOrderLineResult> lines = so.lines().stream()
                .map(l -> new SalesOrderLineResult(
                        l.lineNo(), l.sku(), l.nameSnapshot(), l.unitPriceSnapshot(), l.quantity(),
                        l.reservedQuantity(), l.fulfillmentSource()))
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
