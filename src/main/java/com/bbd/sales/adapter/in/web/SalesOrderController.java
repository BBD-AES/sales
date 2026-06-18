package com.bbd.sales.adapter.in.web;

import com.bbd.sales.adapter.in.web.dto.*;
import com.bbd.sales.application.port.in.SalesOrderUseCase;
import com.bbd.sales.domain.SalesOrderPriority;
import com.bbd.sales.domain.SalesOrderStatus;
import com.bbd.sales.global.security.CurrentUser;
import com.bbd.securitycore.adapter.in.annotation.RequireRole;
import com.bbd.securitycore.application.model.CurrentUserSnapshotResult;
import com.bbd.securitycore.application.port.in.GetCurrentUserSnapshotUseCase;
import com.bbd.securitycore.domain.UserRole;
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
// 인가 정책: 엔드포인트별 @RequireRole 로 역할 게이트.
//   - RoleAuthorizationAspect(AOP)가 JWT의 UserSnapshot.role 을 검증(메서드 우선, 위반=FORBIDDEN_ROLE).
//   - 창고 소유권 등 "데이터 단위" 인가는 역할로 못 거르므로 서비스 계층(authorize*)이 담당.
//   - 신규 엔드포인트는 반드시 @RequireRole 를 직접 달 것(클래스 기본값 없음 = 미부착 시 역할 무제한).
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sales-orders")
public class SalesOrderController {

    private final SalesOrderUseCase salesOrderUseCase;
    private final SalesOrderWebMapper webMapper;

    // 조회(목록): 전 직무 허용. 본사=전체 / 지점=본인창고 스코핑은 서비스에서.
    @RequireRole({UserRole.HQ_MANAGER, UserRole.HQ_STAFF, UserRole.BRANCH_MANAGER, UserRole.BRANCH_STAFF, UserRole.ADMIN})
    @GetMapping
    public SalesOrderPageResponse<SalesOrderSummaryResponse> search(
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) SalesOrderPriority priority,
            @RequestParam(required = false, name = "to_warehouse_code") String toWarehouseCode,
            @RequestParam(required = false, name = "requested_by") String requestedBy,
            @RequestParam(required = false, name = "start_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false, name = "end_date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,   // 기존 0 기본값 버그 -> 20
            CurrentUser currentUser
    ) {
        return webMapper.toSummaryPageResponse(
                salesOrderUseCase.search(webMapper.toSearchQuery(
                        status, priority, toWarehouseCode, requestedBy,
                        startDate, endDate, page, size, currentUser)));
    }

    // 생성: 지점 사용자(+ADMIN)
    @RequireRole({UserRole.BRANCH_STAFF, UserRole.BRANCH_MANAGER, UserRole.ADMIN})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesOrderDetailResponse create(
            @Valid @RequestBody CreateSalesOrderRequest request,
            CurrentUser currentUser
    ) {
        return webMapper.toDetailResponse(
                salesOrderUseCase.create(webMapper.toCreateCommand(request, currentUser)));
    }

    // 조회(상세): 전 직무 허용(지점 본인창고 스코핑은 서비스에서)
    @RequireRole({UserRole.HQ_MANAGER, UserRole.HQ_STAFF, UserRole.BRANCH_MANAGER, UserRole.BRANCH_STAFF, UserRole.ADMIN})
    @GetMapping("/{soNumber}")
    public SalesOrderDetailResponse get(@PathVariable String soNumber, CurrentUser currentUser) {
        return webMapper.toDetailResponse(salesOrderUseCase.get(soNumber, currentUser));
    }

    // 수정: 지점 사용자(+ADMIN), REQUESTED 에서만(상태검증은 도메인)
    @RequireRole({UserRole.BRANCH_STAFF, UserRole.BRANCH_MANAGER, UserRole.ADMIN})
    @PutMapping("/{soNumber}")
    public SalesOrderDetailResponse update(
            @PathVariable String soNumber,
            @Valid @RequestBody UpdateSalesOrderRequest request,
            CurrentUser currentUser
    ) {
        return webMapper.toDetailResponse(
                salesOrderUseCase.update(webMapper.toUpdateCommand(soNumber, request, currentUser)));
    }

    // 제출(->HQ): 지점 관리자(+ADMIN)
    @RequireRole({UserRole.BRANCH_MANAGER, UserRole.ADMIN})
    @PatchMapping("/{soNumber}/submit")
    public SalesOrderStatusChangeResponse submit(@PathVariable String soNumber, CurrentUser currentUser) {
        return webMapper.toStatusChangeResponse(salesOrderUseCase.submit(soNumber, currentUser));
    }

    // 취소: 지점 사용자(+ADMIN)
    @RequireRole({UserRole.BRANCH_STAFF, UserRole.BRANCH_MANAGER, UserRole.ADMIN})
    @PatchMapping("/{soNumber}/cancel")
    public SalesOrderStatusChangeResponse cancel(@PathVariable String soNumber, CurrentUser currentUser) {
        return webMapper.toStatusChangeResponse(salesOrderUseCase.cancel(soNumber, currentUser));
    }

    // 승인: 본사 관리자(+ADMIN)
    @RequireRole({UserRole.HQ_MANAGER, UserRole.ADMIN})
    @PatchMapping("/{soNumber}/approve")
    public SalesOrderStatusChangeResponse approve(@PathVariable String soNumber, CurrentUser currentUser) {
        return webMapper.toStatusChangeResponse(salesOrderUseCase.approve(soNumber, currentUser));
    }

    // 백오더 충족(HQ 결정의 연속): 본사 관리자(+ADMIN)
    @RequireRole({UserRole.HQ_MANAGER, UserRole.ADMIN})
    @PatchMapping("/{soNumber}/fulfill-backorder")
    public SalesOrderStatusChangeResponse fulfillBackorder(@PathVariable String soNumber, CurrentUser currentUser) {
        return webMapper.toStatusChangeResponse(salesOrderUseCase.fulfillBackorder(soNumber, currentUser));
    }

    // 반려: 본사 관리자(+ADMIN)
    @RequireRole({UserRole.HQ_MANAGER, UserRole.ADMIN})
    @PatchMapping("/{soNumber}/reject")
    public SalesOrderStatusChangeResponse reject(
            @PathVariable String soNumber,
            @RequestBody RejectSalesOrderRequest request,
            CurrentUser currentUser
    ) {
        return webMapper.toStatusChangeResponse(
                salesOrderUseCase.reject(soNumber, request.reason(), currentUser));
    }

    // 수령(도착확인): 지점 사용자(+ADMIN)
    @RequireRole({UserRole.BRANCH_STAFF, UserRole.BRANCH_MANAGER, UserRole.ADMIN})
    @PatchMapping("/{soNumber}/receive")
    public SalesOrderStatusChangeResponse receive(@PathVariable String soNumber, CurrentUser currentUser) {
        return webMapper.toStatusChangeResponse(salesOrderUseCase.receive(soNumber, currentUser));
    }
}
