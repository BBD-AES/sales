package com.bbd.sales.adapter.in.web;

import com.bbd.sales.adapter.in.web.dto.*;
import com.bbd.sales.application.port.in.CustomerOrderUseCase;
import com.bbd.sales.domain.CustomerOrderStatus;
import com.bbd.securitycore.adapter.in.annotation.RequireRole;
import com.bbd.securitycore.domain.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

// 인가 정책(SalesOrderController 와 동일 원칙):
//   - 엔드포인트별 @RequireRole 로 역할 게이트(RoleAuthorizationAspect 가 JWT role 검증, 위반=FORBIDDEN_ROLE).
//   - "본인 지점 소유" 등 데이터 단위 인가는 역할로 못 거르므로 서비스 계층(authorizeRead/authorizeOwnerWrite + search 스코핑)이 담당.
//   - 수주(CustomerOrder)는 지점(딜러) 소유 개념 → 쓰기는 지점 사용자(+ADMIN), 조회는 전 직무(HQ=전체, 지점=본인지점).
//   - 신규 엔드포인트는 반드시 @RequireRole 를 직접 달 것(클래스 기본값 없음 = 미부착 시 역할 무제한).
@RestController
@RequiredArgsConstructor
@Validated   // @RequestParam 제약(@Min/@Positive) 활성화
@RequestMapping("/api/v1/customer-orders")
public class CustomerOrderController {
    private final CustomerOrderUseCase customerOrderUseCase; // service가 자동 주입됨
    private final CustomerOrderWebMapper webMapper;

    // 조회(목록): 전 직무 허용. HQ=전체 / 지점=본인지점 스코핑은 서비스에서.
    @RequireRole({UserRole.HQ_MANAGER, UserRole.HQ_STAFF, UserRole.BRANCH_MANAGER, UserRole.BRANCH_STAFF, UserRole.ADMIN})
    @GetMapping
    public SalesOrderPageResponse<CustomerOrderSummaryResponse> search(
            @RequestParam(required = false) CustomerOrderStatus status,
            @RequestParam(required = false, name = "dealer_warehouse_code") String dealerWarehouseCode,
            @RequestParam(required = false, name = "customer_name") String customerName,
            @RequestParam(required = false, name = "requested_by") String requestedBy,
            @RequestParam(required = false, name = "start_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false, name = "end_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive int size
    ) {
        return webMapper.toSummaryPageResponse(
                customerOrderUseCase.search(webMapper.toSearchQuery(status, dealerWarehouseCode, customerName, requestedBy, startDate, endDate, page, size))
        );
    }

    // 생성(수주 접수): 지점 사용자(+ADMIN). 본인지점 한정은 서비스 소유 가드.
    @RequireRole({UserRole.BRANCH_STAFF, UserRole.BRANCH_MANAGER, UserRole.ADMIN})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerOrderDetailResponse create(@Valid @RequestBody CreateCustomerOrderRequest request,
                                              @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 200) String idempotencyKey) {
        return webMapper.toDetailResponse(customerOrderUseCase.create(webMapper.toCreateCommand(request, idempotencyKey)));
    }

    // 조회(상세): 전 직무 허용(지점 본인지점 스코핑은 서비스 authorizeRead).
    @RequireRole({UserRole.HQ_MANAGER, UserRole.HQ_STAFF, UserRole.BRANCH_MANAGER, UserRole.BRANCH_STAFF, UserRole.ADMIN})
    @GetMapping("/{coNumber}")
    public CustomerOrderDetailResponse get(@PathVariable String coNumber) {
        return webMapper.toDetailResponse(customerOrderUseCase.get(coNumber));
    }

    // 수정: 지점 사용자(+ADMIN), OPEN 에서만(상태검증은 도메인)
    @RequireRole({UserRole.BRANCH_STAFF, UserRole.BRANCH_MANAGER, UserRole.ADMIN})
    @PutMapping("/{coNumber}")
    public CustomerOrderDetailResponse update(@PathVariable String coNumber, @Valid @RequestBody UpdateCustomerOrderRequest request) {
        return webMapper.toDetailResponse(customerOrderUseCase.update(webMapper.toUpdateCommand(coNumber, request)));
    }

    // 확정(OPEN->CONFIRMED): 지점 사용자(+ADMIN)
    @RequireRole({UserRole.BRANCH_STAFF, UserRole.BRANCH_MANAGER, UserRole.ADMIN})
    @PatchMapping("/{coNumber}/confirm")
    public CustomerOrderStatusChangeResponse confirm(@PathVariable String coNumber) {
        return webMapper.toStatusChangeResponse(customerOrderUseCase.confirm(coNumber));
    }

    // 취소: 지점 사용자(+ADMIN), OPEN/CONFIRMED 에서만
    @RequireRole({UserRole.BRANCH_STAFF, UserRole.BRANCH_MANAGER, UserRole.ADMIN})
    @PatchMapping("/{coNumber}/cancel")
    public CustomerOrderStatusChangeResponse cancel(@PathVariable String coNumber) {
        return webMapper.toStatusChangeResponse(customerOrderUseCase.cancel(coNumber));
    }

    // 종료(CONFIRMED->CLOSED): 지점 사용자(+ADMIN)
    @RequireRole({UserRole.BRANCH_STAFF, UserRole.BRANCH_MANAGER, UserRole.ADMIN})
    @PatchMapping("/{coNumber}/close")
    public CustomerOrderStatusChangeResponse close(@PathVariable String coNumber) {
        return webMapper.toStatusChangeResponse(customerOrderUseCase.close(coNumber));
    }
}
