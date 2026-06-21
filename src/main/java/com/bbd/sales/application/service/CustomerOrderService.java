package com.bbd.sales.application.service;

import com.bbd.sales.application.command.CreateCustomerOrderCommand;
import com.bbd.sales.application.command.CustomerOrderLineCommand;
import com.bbd.sales.application.command.SearchCustomerOrderQuery;
import com.bbd.sales.application.command.UpdateCustomerOrderCommand;
import com.bbd.sales.application.port.in.CustomerOrderUseCase;
import com.bbd.sales.application.port.out.*;
import com.bbd.sales.application.result.*;
import com.bbd.sales.domain.CustomerOrder;
import com.bbd.sales.domain.CustomerOrderLine;
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

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerOrderService implements CustomerOrderUseCase {

    private final CustomerOrderRepository repository; // 구현(JPA어댑터)은 모르는 채로,out 포트(인터페이스)에만 의존
    private final ItemPort itemPort;
    private final WarehousePort warehousePort;
    private final InventoryPort inventoryPort; // #69: CO 종료 시 지점재고 동기 차감
    private final IdempotencyGuard idempotencyGuard; // #71: 생성 멱등(Idempotency-Key)
    private final CurrentUserProvider currentUserProvider; // 신원은 JWT에서 서버측 취득(컨트롤러 파라미터 아님)

    @Override
    @Transactional(readOnly = true)
    public SalesOrderPageResult<CustomerOrderSummaryResult> search(SearchCustomerOrderQuery query) {
        CurrentUser user = currentUserProvider.current();
        // 본사(HQ/ADMIN): 전달된 딜러 코드 필터 그대로. 지점: 본인지점(이름축)으로 강제, 전달된 코드 필터는 무시(타지점 열람 차단).
        //
        // [테넌시 스코핑이 '이름(tenancyName)축'인 이유 — "코드축이 더 안전"하다는 리뷰(#54) 지적 검토 결과]
        //  코드축이 개명/동명에 견고한 건 맞으나, sales 단독으로 코드축 전환은 불가능하다:
        //   · 인증 신원(user-service 스냅샷 → security-core → CurrentUser)은 tenancyType+tenancyName 만 제공하고,
        //     지점 '코드' 클레임이 org 전역(UserSnapshotResponse·JWT)에 존재하지 않는다 → 비교할 코드가 신원에 없음.
        //   · sales 가 저장하는 dealerWarehouseCode 는 '생성요청 body' 값이라 인가축(현재 사용자 소속)으로는 쓸 수 없다.
        //   ⇒ 코드축 전환은 user-service 가 tenancyCode 를 스냅샷/토큰에 추가하는 'org 계약 변경'이 선행돼야 한다
        //     (그 뒤 CustomerOrder·SalesOrder 가 함께 name→code 이전). 단일 서비스 수정으로 못 닫는다.
        //  [이 설계의 안전 전제 — user-service 거버넌스 책임] tenancyName 은 '유일 + 불변(canonical)'이어야 한다.
        //   이름 중복이면 타지점 열람 누수, 개명이면 과거주문(dealerName 스냅샷) 접근 상실. 전제가 깨지면 위 계약변경이 필요.
        String codeFilter; // HQ 선택 필터(코드축)
        String nameScope;  // 지점 강제 스코핑(이름축 = dealerName)
        if (user.isHq()) {
            codeFilter = query.dealerWarehouseCode();
            nameScope = null;
        } else {
            String warehouseName = user.warehouseName();
            if (warehouseName == null || warehouseName.isBlank()) {
                // 정상 경로에선 resolver 가 BRANCH 의 지점명을 항상 채움. 방어용(인증 컨텍스트 불완전).
                throw new ApiException(ErrorCode.AUTH_HEADER_REQUIRED);
            }
            codeFilter = null;        // 지점이 넘긴 코드 필터 무시 → 타지점 열람 차단
            nameScope = warehouseName;
        }
        LocalDateTime from = query.startDate() != null ? query.startDate().atStartOfDay() : null; // 날짜 경계 변환
        LocalDateTime to = query.endDate() != null ? query.endDate().atTime(LocalTime.MAX) : null;
        CustomerOrderSearchCriteria criteria = new CustomerOrderSearchCriteria( // 권한 반영된 '순수 필터'로 변환
                query.status(), codeFilter, nameScope, query.customerName(), query.requestedBy(), from, to
        );
        CustomerOrderPage page = repository.search(criteria, query.page(), query.size()); // 쿼리 방식(QueryDSL/Specification인지, JPQL/nativeSQL인지 등등)을 모르는 포트로 위임
        List<CustomerOrderSummaryResult> items = page.content().stream().map(this::toSummary).toList(); // 도메인 -> Result 변환
        PaginationResult pagination = new PaginationResult(page.page(), page.size(), page.totalElements(), page.totalPages());
        return new SalesOrderPageResult<>(items, pagination); // 애플리케이션 출력 타입(웹은 이걸 모름)
    }


    @Override
    @Transactional(readOnly = true)
    public CustomerOrderResult get(String coNumber) {
        CustomerOrder co = load(coNumber); // 없으면 404
        authorizeRead(co, currentUserProvider.current()); // HQ는 전체, 그 외는 지점 본인 것만
        return toResult(co); // 도메인 -> 상세 result
    }


    @Override
    public CustomerOrderResult create(CreateCustomerOrderCommand command) {
        CurrentUser user = currentUserProvider.current();
        // #71 멱등: 같은 Idempotency-Key 재요청이면 최초 생성된 수주를 그대로 반환(중복 생성 방지).
        var replay = idempotencyGuard.findReplay(IdempotencyGuard.CO_CREATE, user.employeeNumber(), command.idempotencyKey());
        if (replay.isPresent()) {
            CustomerOrder existing = load(replay.get());
            authorizeOwnerWrite(existing, user); // 재요청도 정상 생성과 동일한 소유권 가드(스코프 변경 시 일관 차단)
            return toResult(existing);
        }
        String dealerName = warehousePort.warehouseName(command.dealerWarehouseCode()); // 딜러명 스냅샷
        // 딜러명 미해결(코드 폴백=조회 실패)이면 fail-fast: 코드-as-이름 박제 방지 + 이름축 인가 오작동 방지.
        if (dealerName == null || dealerName.equals(command.dealerWarehouseCode())) {
            throw new ApiException(ErrorCode.WAREHOUSE_NAME_UNAVAILABLE, command.dealerWarehouseCode());
        }
        // 본인 지점 앞으로만 생성(이름축). ADMIN 예외. 미인가는 라인 해석/채번 전 조기 차단.
        if (!user.isAdmin() && !(user.isBranchUser() && dealerName.equals(user.warehouseName()))) {
            throw new ApiException(ErrorCode.CUSTOMER_ORDER_FORBIDDEN_WAREHOUSE);
        }
        List<CustomerOrderLine> lines = toDomainLines(command.lines()); // sku -> 스냅샷 채워 도메인 라인 생성
        String coNumber = repository.nextCoNumber(); // 채번 (CO-2026-xxxx)
        CustomerOrder co = CustomerOrder.receive(coNumber, command.dealerWarehouseCode(), dealerName, // 생성 규칙은 도메인 생성 메서드에서 검증
                command.customerName(), command.customerContact(), command.note(),
                lines, user.employeeNumber(), LocalDateTime.now());
        CustomerOrder saved = repository.save(co);
        // #71 멱등: 생성 성공 후 키 기록(동시 같은 키면 409 → @Transactional 롤백, 재시도 시 위 findReplay 가 원본 회수).
        idempotencyGuard.record(IdempotencyGuard.CO_CREATE, user.employeeNumber(), command.idempotencyKey(), saved.coNumber());
        return toResult(saved);
    }

    @Override
    public CustomerOrderResult update(UpdateCustomerOrderCommand command) {
        CustomerOrder co = load(command.coNumber());
        authorizeOwnerWrite(co, currentUserProvider.current()); // 본인 지점/admin만 쓰기
        List<CustomerOrderLine> newLines = command.hasLineReplacement() ? toDomainLines(command.lines()) : null; // lines != null일 때만 교체
        co.updateContents(command.note(), newLines); // RECEIVED에서만 쓸 수 있다는 규칙은 도메인이 가짐
        return toResult(repository.save(co));
    }

    @Override
    public CustomerOrderStatusChangeResult confirm(String coNumber) {
        CurrentUser u = currentUserProvider.current();
        CustomerOrder co = load(coNumber);
        authorizeOwnerWrite(co, u);
        co.confirm(u.employeeNumber(), LocalDateTime.now()); // 상태전이 규칙(canConfirm)은 도메인이 가짐
        repository.save(co);
        return statusChange(co, u.employeeNumber(), co.confirmedAt());
    }

    // cancel / close 동일 패턴(도메인 cancel()/close()에 규칙 위임 후 저장)
    @Override
    public CustomerOrderStatusChangeResult cancel(String coNumber) {
        CurrentUser u = currentUserProvider.current();
        CustomerOrder co = load(coNumber);
        authorizeOwnerWrite(co, u);
        co.cancel(u.employeeNumber(), LocalDateTime.now());
        repository.save(co);
        return statusChange(co, u.employeeNumber(), co.canceledAt());
    }

    @Override
    public CustomerOrderStatusChangeResult close(String coNumber) {
        CurrentUser currentUser = currentUserProvider.current();
        CustomerOrder co = load(coNumber);
        authorizeOwnerWrite(co, currentUser);
        co.close(currentUser.employeeNumber(), LocalDateTime.now()); // CONFIRMED→CLOSED 검증·전이(비CONFIRMED 면 NOT_CLOSABLE)
        // #69: 종료 = 고객 인도 = 지점재고 물리 차감. inventory 동기 호출 → 부족 시 예외 → @Transactional 롤백(save 미호출) → 종료 안 됨.
        //  재고는 sync 원칙(예약과 동일 TOCTOU; 지점재고는 HQ 예약과 공유 자원이라 async 면 오버셀).
        // [동시성/멱등] close 에 비관락을 '안' 건다 — 외부효과가 동기 REST 라, 락을 걸면 #55 가 reserveLine 에서 제거한 '락-중-네트워크IO'를
        //   재도입한다. 동시 close 2건/재시도의 이중 차감은 inventory 의 referenceNumber(=coNumber) '레이스세이프 dedup'(핸드오프 필수)이 막는다
        //   — reserveLine 의 requestId dedup 과 동일 모델. (HTTP read-timeout 으로 hang 시 커넥션 점유도 상한.)
        // [잔여] 차감 성공 후 로컬 save/commit 실패 시 orphan 차감 가능 — 위 dedup 으로 재시도가 안전(이중 차감 없음). 보상/리컨실은 후속.
        inventoryPort.shipForCustomerOrder(co.coNumber(), toStockOutLines(co));
        repository.save(co);
        return statusChange(co, currentUser.employeeNumber(), co.closedAt());
    }

    // #69: CO 라인 → inventory 출고 라인(지점 창고 = dealerWarehouseCode, 단가는 스냅샷 정수부).
    private List<StockOutLine> toStockOutLines(CustomerOrder co) {
        return co.lines().stream()
                .map(l -> new StockOutLine(l.sku(), l.quantity(), co.dealerWarehouseCode(), l.unitPriceSnapshot().intValue()))
                .toList();
    }

    // 공통 조회 + 404
    private CustomerOrder load(String coNumber) {
        return repository.findByCoNumber(coNumber).orElseThrow(() ->
                new ApiException(ErrorCode.CUSTOMER_ORDER_NOT_FOUND));
    }

    // 조회 권한: HQ는 전체, 지점은 본인 것만 (이름축 — 근거/안전전제는 search() 의 [테넌시 스코핑] 주석 참조)
    private void authorizeRead(CustomerOrder co, CurrentUser u) {
        if (u.isHq()) return; // 본사(ADMIN/HQ_*)는 전 지점 조회
        if (!co.ownedByWarehouseName(u.warehouseName())) {
            throw new ApiException(ErrorCode.CUSTOMER_ORDER_FORBIDDEN_WAREHOUSE);
        }
    }

    // 쓰기 권한: admin 또는 (지점유저 && 본인소유)
    private void authorizeOwnerWrite(CustomerOrder co, CurrentUser u) {
        if (u.isAdmin()) return;
        if (!(u.isBranchUser() && co.ownedByWarehouseName(u.warehouseName()))) {
            throw new ApiException(ErrorCode.CUSTOMER_ORDER_FORBIDDEN_WAREHOUSE);
        }
    }

    // sku -> 상품 스냅샷 채워 라인 생성
    private List<CustomerOrderLine> toDomainLines(List<CustomerOrderLineCommand> cmds) {
        List<CustomerOrderLine> lines = new ArrayList<>();
        List<String> inactive = new ArrayList<>();
        int lineNo = 1;
        for (CustomerOrderLineCommand lc : cmds) {
            ProductSnapshot p = itemPort.resolveProduct(lc.sku()); // 미존재(404)면 예외 전파(범위 밖)
            if (!p.active()) {
                inactive.add(p.sku());
                continue;
            }
            lines.add(new CustomerOrderLine(lineNo++, p.sku(), p.name(), p.unitPrice(), lc.quantity()));
        }
        if (!inactive.isEmpty()) {
            throw new ApiException(ErrorCode.ITEM_NOT_ORDERABLE, "주문 불가(비활성) SKU:  " + String.join(", ", inactive));
        }
        return lines;
    }

    // toResult/toSummary/statusChange = 도메인 -> 앱 출력(Result) 변환기
    private CustomerOrderResult toResult(CustomerOrder co) {
        List<CustomerOrderLineResult> lines = co.lines().stream()
                .map(l -> new CustomerOrderLineResult(l.lineNo(), l.sku(), l.nameSnapshot(), l.unitPriceSnapshot(), l.quantity()))
                .toList();
        return new CustomerOrderResult(co.coNumber(), co.dealerWarehouseCode(), co.dealerName(),
                co.customerName(), co.customerContact(), co.status(),
                co.requestedBy(), co.confirmedBy(), co.canceledBy(), co.closedBy(),
                co.requestedAt(), co.confirmedAt(), co.canceledAt(), co.closedAt(),
                co.totalAmount(), co.note(), lines);
    }

    private CustomerOrderStatusChangeResult statusChange(CustomerOrder co, String actor, LocalDateTime at) {
        return new CustomerOrderStatusChangeResult(co.coNumber(), co.status(), actor, at);
    }


    private CustomerOrderSummaryResult toSummary(CustomerOrder co) {
        return new CustomerOrderSummaryResult(co.coNumber(), co.dealerWarehouseCode(), co.dealerName(),
                co.customerName(), co.status(),
                co.requestedBy(), co.confirmedBy(), co.canceledBy(), co.closedBy(),
                co.requestedAt(), co.confirmedAt(), co.canceledAt(), co.closedAt(),
                co.totalAmount(), co.note());
    }
}
