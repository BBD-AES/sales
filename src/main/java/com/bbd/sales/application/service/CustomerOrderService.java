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

    @Override
    @Transactional(readOnly = true)
    public SalesOrderPageResult<CustomerOrderSummaryResult> search(SearchCustomerOrderQuery query) {
        String dealerScope = query.dealerWarehouseCode(); // 딜러 필터
        if (!query.currentUser().isHq()) {
            String wc = query.currentUser().warehouseCode();
            // HQ 아닌데 창고코드 없으면 차단함
            if (wc == null || wc.isBlank()) {
                throw new ApiException(ErrorCode.AUTH_HEADER_REQUIRED);
            }
            dealerScope = wc; // 볼 수 있는 지점을 본인 지점으로 강제함
        }
        LocalDateTime from = query.startDate() != null ? query.startDate().atStartOfDay() : null; // 날짜 경계 변환
        LocalDateTime to = query.endDate() != null ? query.endDate().atTime(LocalTime.MAX) : null;
        CustomerOrderSearchCriteria criteria = new CustomerOrderSearchCriteria( // 권한 반영된 '순수 필터'로 변환
                query.status(), dealerScope, query.customerName(), query.requestedBy(), from, to
        );
        CustomerOrderPage page = repository.search(criteria, query.page(), query.size()); // 쿼리 방식(QueryDSL/Specification인지, JPQL/nativeSQL인지 등등)을 모르는 포트로 위임
        List<CustomerOrderSummaryResult> items = page.content().stream().map(this::toSummary).toList(); // 도메인 -> Result 변환
        PaginationResult pagination = new PaginationResult(page.page(), page.size(), page.totalElements(), page.totalPages());
        return new SalesOrderPageResult<>(items, pagination); // 애플리케이션 출력 타입(웹은 이걸 모름)
    }


    @Override
    @Transactional(readOnly = true)
    public CustomerOrderResult get(String coNumber, CurrentUser currentUser) {
        CustomerOrder co = load(coNumber); // 없으면 404
        authorizeRead(co, currentUser); // HQ는 전체, 그 외는 지점 본인 것만
        return toResult(co); // 도메인 -> 상세 result
    }


    @Override
    public CustomerOrderResult create(CreateCustomerOrderCommand command) {
        CurrentUser user = command.currentUser();
        if (!user.isAdmin() && !(user.isBranchUser() && command.dealerWarehouseCode().equals(user.warehouseCode()))) { // 지점유저(본인 지점)만 생성, admin 예외
            throw new ApiException(ErrorCode.CUSTOMER_ORDER_FORBIDDEN_WAREHOUSE);
        }
        List<CustomerOrderLine> lines = toDomainLines(command.lines()); // sku -> 스냅샷 채워 도메인 라인 생성
        String coNumber = repository.nextCoNumber(); // 채번 (CO-2026-xxxx)
        String dealerName = warehousePort.warehouseName(command.dealerWarehouseCode()); // 딜러명 스냅샷
        CustomerOrder co = CustomerOrder.receive(coNumber, command.dealerWarehouseCode(), dealerName, // 생성 규칙은 도메인 생성 메서드에서 검증
                command.customerName(), command.customerContact(), command.note(),
                lines, user.employeeNumber(), LocalDateTime.now());
        return toResult(repository.save(co)); // 저장 후 Result
        
    }

    @Override
    public CustomerOrderResult update(UpdateCustomerOrderCommand command) {
        CustomerOrder co = load(command.coNumber());
        authorizeOwnerWrite(co, command.currentUser()); // 본인 지점/admin만 쓰기
        List<CustomerOrderLine> newLines = command.hasLineReplacement() ? toDomainLines(command.lines()) : null; // lines != null일 때만 교체
        co.updateContents(command.note(), newLines); // RECEIVED에서만 쓸 수 있다는 규칙은 도메인이 가짐
        return toResult(repository.save(co));
    }

    @Override
    public CustomerOrderStatusChangeResult confirm(String coNumber, CurrentUser u) {
        CustomerOrder co = load(coNumber);
        authorizeOwnerWrite(co, u);
        co.confirm(u.employeeNumber(), LocalDateTime.now()); // 상태전이 규칙(canConfirm)은 도메인이 가짐
        repository.save(co);
        return statusChange(co, u.employeeNumber(), co.confirmedAt());
    }

    // cancel / close 동일 패턴(도메인 cancel()/close()에 규칙 위임 후 저장)
    @Override
    public CustomerOrderStatusChangeResult cancel(String coNumber, CurrentUser u) {
        CustomerOrder co = load(coNumber);
        authorizeOwnerWrite(co, u);
        co.cancel(u.employeeNumber(), LocalDateTime.now());
        repository.save(co);
        return statusChange(co, u.employeeNumber(), co.canceledAt());
    }

    @Override
    public CustomerOrderStatusChangeResult close(String coNumber, CurrentUser currentUser) {
        CustomerOrder co = load(coNumber);
        authorizeOwnerWrite(co, currentUser);
        co.close(currentUser.employeeNumber(), LocalDateTime.now());
        repository.save(co);
        return statusChange(co, currentUser.employeeNumber(), co.closedAt());
    }

    // 공통 조회 + 404
    private CustomerOrder load(String coNumber) {
        return repository.findByCoNumber(coNumber).orElseThrow(() ->
                new ApiException(ErrorCode.CUSTOMER_ORDER_NOT_FOUND));
    }

    // 조회 권한: HQ는 전체, 지점은 본인 것만
    private void authorizeRead(CustomerOrder co, CurrentUser u) { //
        if (u.isHq()) return;
        if (!co.ownedByWarehouse(u.warehouseCode())) {
            throw new ApiException(ErrorCode.CUSTOMER_ORDER_FORBIDDEN_WAREHOUSE);
        }
    }

    // 쓰기 권한: admin 또는 (지점유저 && 본인소유)
    private void authorizeOwnerWrite(CustomerOrder co, CurrentUser u) {
        if (u.isAdmin()) return;
        if (!(u.isBranchUser() && co.ownedByWarehouse(u.warehouseCode()))) {
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
