package com.bbd.sales.application.service;

import com.bbd.sales.application.command.CreateCustomerOrderCommand;
import com.bbd.sales.application.command.CustomerOrderLineCommand;
import com.bbd.sales.application.command.UpdateCustomerOrderCommand;
import com.bbd.sales.application.port.out.CurrentUserProvider;
import com.bbd.sales.application.port.out.CustomerOrderRepository;
import com.bbd.sales.application.port.out.ItemPort;
import com.bbd.sales.application.port.out.ProductSnapshot;
import com.bbd.sales.application.port.out.WarehousePort;
import com.bbd.sales.application.result.CustomerOrderResult;
import com.bbd.sales.application.result.CustomerOrderStatusChangeResult;
import com.bbd.sales.domain.CustomerOrder;
import com.bbd.sales.domain.CustomerOrderLine;
import com.bbd.sales.domain.CustomerOrderStateException;
import com.bbd.sales.domain.CustomerOrderStatus;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import com.bbd.sales.global.security.CurrentUser;
import com.bbd.sales.global.security.RoleType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CustomerOrderService 유스케이스 단위테스트.
 * out 포트(CustomerOrderRepository, ItemPort/WarehousePort, CurrentUserProvider)를 Mockito 목으로 두고
 * 생성/조회 권한·상태전이 위임·라인 교체 분기·404 로드 실패를 검증한다.
 * 신원은 CurrentUserProvider(목)로 주입 — 컨트롤러 파라미터가 아니라 서버측 취득.
 * DB·스프링 컨텍스트 없이 순수 협력 검증.
 */
@ExtendWith(MockitoExtension.class)
class CustomerOrderServiceTest {

    @Mock CustomerOrderRepository repository;
    @Mock ItemPort itemPort;
    @Mock WarehousePort warehousePort;
    @Mock CurrentUserProvider currentUserProvider;

    @InjectMocks CustomerOrderService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 3, 10, 0);

    // 지점 창고코드
    private static final String WH = "WH-BR-001";

    // HQ(전체 조회 가능, admin 아님), ADMIN(전체 쓰기 가능), 본인 지점 유저, 타 지점 유저
    private static final CurrentUser HQ = new CurrentUser("HQ001", RoleType.HQ_MANAGER, null);
    private static final CurrentUser ADMIN = new CurrentUser("AD001", RoleType.ADMIN, null);
    private static final CurrentUser BRANCH = new CurrentUser("BR003", RoleType.BRANCH_STAFF, WH);
    private static final CurrentUser OTHER_BRANCH = new CurrentUser("BR009", RoleType.BRANCH_STAFF, "WH-BR-999");

    /** OPEN(수주 접수) 상태의 단일 라인 수주 — WH 지점 소유. */
    private CustomerOrder open(String coNumber) {
        return CustomerOrder.receive(coNumber, WH, "강남 1지점",
                "홍길동", "010-1234-5678", "메모",
                List.of(new CustomerOrderLine(1, "OIL-FLT-001", "오일필터", new BigDecimal("1000"), 2)),
                "BR003", NOW);
    }

    // ---------------------------------------------------------------------
    // create
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("create: 카탈로그로 라인 스냅샷 매핑 + 채번 + 저장, Result 반환")
    void create_happyPath() {
        CreateCustomerOrderCommand command = new CreateCustomerOrderCommand(
                WH, "홍길동", "010-1234-5678", "메모",
                List.of(new CustomerOrderLineCommand("OIL-FLT-001", 2)));

        when(currentUserProvider.current()).thenReturn(BRANCH);
        when(itemPort.resolveProduct("OIL-FLT-001"))
                .thenReturn(new ProductSnapshot("OIL-FLT-001", "오일필터", new BigDecimal("1500")));
        when(repository.nextCoNumber()).thenReturn("CO-2026-0001");
        when(warehousePort.warehouseName(WH)).thenReturn("강남 1지점");
        when(repository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerOrderResult result = service.create(command);

        // 채번/저장 호출
        verify(repository).nextCoNumber();
        ArgumentCaptor<CustomerOrder> saved = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(repository).save(saved.capture());

        // 저장된 도메인이 카탈로그 스냅샷(이름/단가)으로 채워졌는지
        CustomerOrder co = saved.getValue();
        assertThat(co.coNumber()).isEqualTo("CO-2026-0001");
        assertThat(co.dealerWarehouseCode()).isEqualTo(WH);
        assertThat(co.dealerName()).isEqualTo("강남 1지점");
        assertThat(co.status()).isEqualTo(CustomerOrderStatus.OPEN);
        assertThat(co.lines()).hasSize(1);
        assertThat(co.lines().get(0).nameSnapshot()).isEqualTo("오일필터");
        assertThat(co.lines().get(0).unitPriceSnapshot()).isEqualByComparingTo("1500");
        assertThat(co.lines().get(0).quantity()).isEqualTo(2);

        // 반환 Result도 동일 식별자/상태
        assertThat(result.coNumber()).isEqualTo("CO-2026-0001");
        assertThat(result.status()).isEqualTo(CustomerOrderStatus.OPEN);
        assertThat(result.totalAmount()).isEqualByComparingTo("3000"); // 1500 * 2
        assertThat(result.lines()).hasSize(1);
    }

    @Test
    @DisplayName("create: ADMIN은 본인 지점이 아니어도 생성 가능")
    void create_admin_otherWarehouseAllowed() {
        CreateCustomerOrderCommand command = new CreateCustomerOrderCommand(
                "WH-BR-777", "홍길동", "010-1234-5678", "메모",
                List.of(new CustomerOrderLineCommand("OIL-FLT-001", 1)));

        when(currentUserProvider.current()).thenReturn(ADMIN);
        when(itemPort.resolveProduct("OIL-FLT-001"))
                .thenReturn(new ProductSnapshot("OIL-FLT-001", "오일필터", new BigDecimal("1000")));
        when(repository.nextCoNumber()).thenReturn("CO-2026-0002");
        when(warehousePort.warehouseName("WH-BR-777")).thenReturn("창고777");
        when(repository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerOrderResult result = service.create(command);

        assertThat(result.coNumber()).isEqualTo("CO-2026-0002");
        assertThat(result.dealerWarehouseCode()).isEqualTo("WH-BR-777");
        verify(repository).save(any(CustomerOrder.class));
    }

    @Test
    @Disabled("#53 인증 마이그레이션 중 — CustomerOrderService.create 창고권한 체크 주석(런타임 인증 OFF). 인증 복구 시 재활성")
    @DisplayName("create: 지점유저가 본인 외 창고로 생성하면 FORBIDDEN_WAREHOUSE, 채번/저장 안 함")
    void create_branchUser_otherWarehouse_forbidden() {
        CreateCustomerOrderCommand command = new CreateCustomerOrderCommand(
                "WH-BR-999", "홍길동", "010-1234-5678", "메모",
                List.of(new CustomerOrderLineCommand("OIL-FLT-001", 1)));

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CUSTOMER_ORDER_FORBIDDEN_WAREHOUSE);

        verify(repository, never()).nextCoNumber();
        verify(repository, never()).save(any());
        verify(itemPort, never()).resolveProduct(any());
    }

    // ---------------------------------------------------------------------
    // get + authorizeRead
    // ---------------------------------------------------------------------

    @Test
    @Disabled("#53 인증 마이그레이션 중 — CustomerOrderService.authorizeRead의 HQ 바이패스 주석(HQ도 창고체크에 걸림). 인증 복구 시 재활성")
    @DisplayName("get: HQ는 타 지점 수주도 전체 조회 통과")
    void get_hq_passesAnyWarehouse() {
        CustomerOrder co = open("CO-2026-0001");
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));

        CustomerOrderResult result = service.get("CO-2026-0001");

        assertThat(result.coNumber()).isEqualTo("CO-2026-0001");
        assertThat(result.dealerWarehouseCode()).isEqualTo(WH);
    }

    @Test
    @DisplayName("get: 지점유저는 본인 소유 수주 조회 통과")
    void get_branchUser_ownWarehouse_passes() {
        CustomerOrder co = open("CO-2026-0001");
        when(currentUserProvider.current()).thenReturn(BRANCH);
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));

        CustomerOrderResult result = service.get("CO-2026-0001");

        assertThat(result.coNumber()).isEqualTo("CO-2026-0001");
    }

    @Test
    @DisplayName("get: 지점유저가 타 지점 수주를 조회하면 FORBIDDEN_WAREHOUSE")
    void get_branchUser_otherWarehouse_forbidden() {
        CustomerOrder co = open("CO-2026-0001");
        when(currentUserProvider.current()).thenReturn(OTHER_BRANCH);
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));

        assertThatThrownBy(() -> service.get("CO-2026-0001"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CUSTOMER_ORDER_FORBIDDEN_WAREHOUSE);
    }

    // ---------------------------------------------------------------------
    // load 실패
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("get: 존재하지 않는 수주는 CUSTOMER_ORDER_NOT_FOUND")
    void get_notFound_throws() {
        when(repository.findByCoNumber("CO-NONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("CO-NONE"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CUSTOMER_ORDER_NOT_FOUND);
    }

    // ---------------------------------------------------------------------
    // confirm / cancel / close — 도메인 메서드 위임 + save + 상태변경 결과
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("confirm: OPEN -> CONFIRMED 전이 후 저장, 상태변경 결과 반환")
    void confirm_open_toConfirmed() {
        CustomerOrder co = open("CO-2026-0001");
        when(currentUserProvider.current()).thenReturn(BRANCH);
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));
        when(repository.save(co)).thenReturn(co);

        CustomerOrderStatusChangeResult result = service.confirm("CO-2026-0001");

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.CONFIRMED);
        assertThat(co.confirmedBy()).isEqualTo("BR003");
        verify(repository).save(co);
        assertThat(result.coNumber()).isEqualTo("CO-2026-0001");
        assertThat(result.status()).isEqualTo(CustomerOrderStatus.CONFIRMED);
        assertThat(result.actor()).isEqualTo("BR003");
        assertThat(result.changedAt()).isEqualTo(co.confirmedAt());
    }

    @Test
    @DisplayName("confirm: ADMIN은 타 지점 수주도 확정 가능(쓰기 권한 admin 분기)")
    void confirm_admin_otherWarehouseAllowed() {
        CustomerOrder co = open("CO-2026-0001"); // WH 소유, ADMIN은 warehouseName=null
        when(currentUserProvider.current()).thenReturn(ADMIN);
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));
        when(repository.save(co)).thenReturn(co);

        CustomerOrderStatusChangeResult result = service.confirm("CO-2026-0001");

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.CONFIRMED);
        assertThat(co.confirmedBy()).isEqualTo("AD001");
        verify(repository).save(co);
        assertThat(result.status()).isEqualTo(CustomerOrderStatus.CONFIRMED);
        assertThat(result.actor()).isEqualTo("AD001");
    }

    @Test
    @DisplayName("cancel: OPEN -> CANCELED 전이 후 저장, 상태변경 결과 반환")
    void cancel_open_toCanceled() {
        CustomerOrder co = open("CO-2026-0001");
        when(currentUserProvider.current()).thenReturn(BRANCH);
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));
        when(repository.save(co)).thenReturn(co);

        CustomerOrderStatusChangeResult result = service.cancel("CO-2026-0001");

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.CANCELED);
        assertThat(co.canceledBy()).isEqualTo("BR003");
        verify(repository).save(co);
        assertThat(result.status()).isEqualTo(CustomerOrderStatus.CANCELED);
        assertThat(result.changedAt()).isEqualTo(co.canceledAt());
    }

    @Test
    @DisplayName("cancel: CONFIRMED -> CANCELED도 허용(isCancelable의 CONFIRMED 분기)")
    void cancel_confirmed_toCanceled() {
        CustomerOrder co = open("CO-2026-0001");
        co.confirm("BR003", NOW); // CONFIRMED 선행
        when(currentUserProvider.current()).thenReturn(BRANCH);
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));
        when(repository.save(co)).thenReturn(co);

        CustomerOrderStatusChangeResult result = service.cancel("CO-2026-0001");

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.CANCELED);
        assertThat(co.canceledBy()).isEqualTo("BR003");
        verify(repository).save(co);
        assertThat(result.status()).isEqualTo(CustomerOrderStatus.CANCELED);
        assertThat(result.changedAt()).isEqualTo(co.canceledAt());
    }

    @Test
    @DisplayName("close: CONFIRMED -> CLOSED 전이 후 저장, 상태변경 결과 반환")
    void close_confirmed_toClosed() {
        CustomerOrder co = open("CO-2026-0001");
        co.confirm("BR003", NOW); // CONFIRMED 선행 (close는 CONFIRMED에서만 가능)
        when(currentUserProvider.current()).thenReturn(BRANCH);
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));
        when(repository.save(co)).thenReturn(co);

        CustomerOrderStatusChangeResult result = service.close("CO-2026-0001");

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.CLOSED);
        assertThat(co.closedBy()).isEqualTo("BR003");
        verify(repository).save(co);
        assertThat(result.status()).isEqualTo(CustomerOrderStatus.CLOSED);
        assertThat(result.changedAt()).isEqualTo(co.closedAt());
    }

    @Test
    @DisplayName("close: OPEN 상태면 도메인 상태규칙 위반(CustomerOrderStateException) 전파, 저장 안 함")
    void close_open_domainStateViolation_noSave() {
        CustomerOrder co = open("CO-2026-0001"); // OPEN, close는 CONFIRMED에서만 가능
        when(currentUserProvider.current()).thenReturn(BRANCH);
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));

        assertThatThrownBy(() -> service.close("CO-2026-0001"))
                .isInstanceOf(CustomerOrderStateException.class)
                .extracting("violation")
                .isEqualTo(CustomerOrderStateException.Violation.NOT_CLOSABLE);

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.OPEN);
        verify(repository, never()).save(any());
    }

    @Test
    @Disabled("#53 인증 마이그레이션 중 — CustomerOrderService.authorizeOwnerWrite 주석(런타임 인증 OFF). 인증 복구 시 재활성")
    @DisplayName("confirm: 타 지점 유저면 FORBIDDEN_WAREHOUSE, 도메인 전이/저장 안 함")
    void confirm_otherBranch_forbidden_noSave() {
        CustomerOrder co = open("CO-2026-0001");
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));

        assertThatThrownBy(() -> service.confirm("CO-2026-0001"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CUSTOMER_ORDER_FORBIDDEN_WAREHOUSE);

        assertThat(co.status()).isEqualTo(CustomerOrderStatus.OPEN);
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // update — hasLineReplacement true/false 분기
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("update: lines 있으면(hasLineReplacement=true) 카탈로그로 라인 교체")
    void update_withLineReplacement_resolvesProducts() {
        CustomerOrder co = open("CO-2026-0001");
        UpdateCustomerOrderCommand command = new UpdateCustomerOrderCommand(
                "CO-2026-0001", "수정메모",
                List.of(new CustomerOrderLineCommand("RLY-12V-30A-01", 5)));

        when(currentUserProvider.current()).thenReturn(BRANCH);
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));
        when(itemPort.resolveProduct("RLY-12V-30A-01"))
                .thenReturn(new ProductSnapshot("RLY-12V-30A-01", "릴레이", new BigDecimal("8500")));
        when(repository.save(co)).thenReturn(co);

        CustomerOrderResult result = service.update(command);

        // 라인 교체 경로 -> resolveProduct 호출됨
        verify(itemPort).resolveProduct("RLY-12V-30A-01");
        verify(repository).save(co);
        assertThat(co.note()).isEqualTo("수정메모");
        assertThat(co.lines()).hasSize(1);
        assertThat(co.lines().get(0).sku()).isEqualTo("RLY-12V-30A-01");
        assertThat(co.lines().get(0).nameSnapshot()).isEqualTo("릴레이");
        assertThat(co.lines().get(0).quantity()).isEqualTo(5);
        assertThat(result.lines().get(0).sku()).isEqualTo("RLY-12V-30A-01");
    }

    @Test
    @DisplayName("update: lines 없으면(hasLineReplacement=false) 라인 유지, 카탈로그 미조회")
    void update_withoutLineReplacement_keepsLines() {
        CustomerOrder co = open("CO-2026-0001");
        UpdateCustomerOrderCommand command = new UpdateCustomerOrderCommand(
                "CO-2026-0001", "메모만수정", null);

        when(currentUserProvider.current()).thenReturn(BRANCH);
        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));
        when(repository.save(co)).thenReturn(co);

        CustomerOrderResult result = service.update(command);

        // 라인 미교체 -> resolveProduct 호출 없음, 기존 라인 그대로
        verify(itemPort, never()).resolveProduct(any());
        verify(repository).save(co);
        assertThat(co.note()).isEqualTo("메모만수정");
        assertThat(co.lines()).hasSize(1);
        assertThat(co.lines().get(0).sku()).isEqualTo("OIL-FLT-001");
        assertThat(result.note()).isEqualTo("메모만수정");
    }

    @Test
    @Disabled("#53 인증 마이그레이션 중 — CustomerOrderService.authorizeOwnerWrite 주석(런타임 인증 OFF). 인증 복구 시 재활성")
    @DisplayName("update: 타 지점 유저면 FORBIDDEN_WAREHOUSE, 카탈로그/저장 안 함")
    void update_otherBranch_forbidden() {
        CustomerOrder co = open("CO-2026-0001");
        UpdateCustomerOrderCommand command = new UpdateCustomerOrderCommand(
                "CO-2026-0001", "메모", null);

        when(repository.findByCoNumber("CO-2026-0001")).thenReturn(Optional.of(co));

        assertThatThrownBy(() -> service.update(command))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CUSTOMER_ORDER_FORBIDDEN_WAREHOUSE);

        verify(itemPort, never()).resolveProduct(any());
        verify(repository, never()).save(any());
    }
}
