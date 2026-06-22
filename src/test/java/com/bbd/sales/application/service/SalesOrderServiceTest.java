package com.bbd.sales.application.service;

import com.bbd.sales.application.command.CreateSalesOrderCommand;
import com.bbd.sales.application.command.SalesOrderLineCommand;
import com.bbd.sales.application.command.ReserveLineCommand;
import com.bbd.sales.application.port.out.*;
import com.bbd.sales.application.command.SearchSalesOrderQuery;
import com.bbd.sales.application.command.UpdateSalesOrderCommand;
import com.bbd.sales.domain.*;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import com.bbd.sales.global.security.CurrentUser;
import com.bbd.sales.global.security.RoleType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOrderService 유스케이스 단위테스트.
 * out 포트를 Mockito 목으로 두고 오케스트레이션 분기(전량예약 / 부족분 BUY·MAKE / 권한·상태 가드)를 검증한다.
 * 신원은 CurrentUserProvider(목)로 주입한다 — 컨트롤러 파라미터가 아니라 서버측 취득.
 * DB·스프링 컨텍스트 없이 순수 협력 검증.
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderServiceTest {

    @Mock
    SalesOrderRepository repository;
    @Mock
    InventoryPort inventoryPort;
    @Mock
    SalesOrderEventPublisher eventPublisher;
    @Mock
    ItemPort itemPort;
    @Mock
    WarehousePort warehousePort;
    @Mock
    ProcurementPort procurementPort;
    @Mock
    CurrentUserProvider currentUserProvider;
    @Mock
    IdempotencyGuard idempotencyGuard;

    @InjectMocks
    SalesOrderService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 3, 10, 0);
    private static final CurrentUser HQ = new CurrentUser("HQ001", RoleType.HQ_MANAGER, null);
    private static final CurrentUser ADMIN = new CurrentUser("AD001", RoleType.ADMIN, null);
    private static final CurrentUser STAFF = new CurrentUser("BR003", RoleType.BRANCH_STAFF, "강남 1지점");
    private static final CurrentUser OTHER_BRANCH = new CurrentUser("BR009", RoleType.BRANCH_STAFF, "분당 1지점");

    /**
     * SUBMITTED 상태의 단일 라인 출고요청.
     */
    private SalesOrder submitted(String sku, int qty) {
        SalesOrder so = SalesOrder.request("SO-1", "WH-BR-001", "강남 1지점",
                SalesOrderPriority.NORMAL, null,
                List.of(new SalesOrderLine(1, sku, "상품", new BigDecimal("1000"), qty)),
                "BR003", NOW);
        so.submit(NOW);
        return so;
    }

    /** REQUESTED 상태 단일 라인(취소 등 REQUESTED 전용 테스트용). */
    private SalesOrder requestedOrder(String sku, int qty) {
        return SalesOrder.request("SO-1", "WH-BR-001", "강남 1지점",
                SalesOrderPriority.NORMAL, null,
                List.of(new SalesOrderLine(1, sku, "상품", new BigDecimal("1000"), qty)),
                "BR003", NOW);
    }

    @Test
    @DisplayName("approve: 사전 전량 예약된 상태 확정 -> IN_FULFILLMENT, 구매요청 없음")
    void approve_allReserved_inFulfillment() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        so.reserveLine("OIL-FLT-001", 10); // 사전 전량 예약(SUBMITTED 유지)
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        service.approve("SO-1");

        assertThat(so.status()).isEqualTo(SalesOrderStatus.IN_FULFILLMENT);
        assertThat(so.lines().get(0).fulfillmentSource()).isEqualTo(FulfillmentSource.STOCK);
        verify(procurementPort, never()).requestPurchase(any(), any(), anyList());
    }

    @Test
    @DisplayName("approve: 부족분 남은 채 확정 -> BACKORDERED + 부족분 구매요청(PR)")
    void approve_shortfall_backordered_requestsPurchase() {
        SalesOrder so = submitted("RLY-12V-30A-01", 5);
        so.reserveLine("RLY-12V-30A-01", 2); // 부분 예약(부족 3)
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        service.approve("SO-1");

        assertThat(so.status()).isEqualTo(SalesOrderStatus.BACKORDERED);
        assertThat(so.lines().get(0).reservedQuantity()).isEqualTo(2); // 부족 3 남음
        assertThat(so.lines().get(0).fulfillmentSource()).isEqualTo(FulfillmentSource.BACKORDERED);
        verify(procurementPort).requestPurchase(eq("SO-1"), eq("WH-BR-001"), anyList());
    }

    @Test
    @DisplayName("approve: 예약 0이면 -> BACKORDERED + 전량 구매요청")
    void approve_nothingReserved_backordered() {
        SalesOrder so = submitted("RLY-12V-30A-01", 5);
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        service.approve("SO-1");

        assertThat(so.status()).isEqualTo(SalesOrderStatus.BACKORDERED);
        assertThat(so.lines().get(0).reservedQuantity()).isZero();
        verify(procurementPort).requestPurchase(eq("SO-1"), eq("WH-BR-001"), anyList());
    }

    @Test
    @DisplayName("approve: HQ 권한 아니면 거부, 구매요청 안 함")
    void approve_nonHqRole_forbidden() {
        when(currentUserProvider.current()).thenReturn(STAFF);

        assertThatThrownBy(() -> service.approve("SO-1"))
                .isInstanceOf(ApiException.class);

        // 역할 인가가 락/로드 전에 차단 → repository 미접근(특히 락 미획득). CodeRabbit #55.
        verify(repository, never()).lockForUpdate(any());
        verify(procurementPort, never()).requestPurchase(any(), any(), anyList());
    }

    @Test
    @DisplayName("approve: SUBMITTED 아니면 NOT_DECIDABLE, 구매요청 안 함")
    void approve_notSubmitted_throws() {
        SalesOrder requested = SalesOrder.request("SO-1", "WH-BR-001", "강남 1지점",
                SalesOrderPriority.NORMAL, null,
                List.of(new SalesOrderLine(1, "OIL-FLT-001", "상품", new BigDecimal("1000"), 10)),
                "BR003", NOW); // REQUESTED(미제출)
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> service.approve("SO-1"))
                .isInstanceOf(SalesOrderStateException.class);

        verify(procurementPort, never()).requestPurchase(any(), any(), anyList());
    }

    @Test
    @DisplayName("reserveLine: 사람이 고른 창고에서 예약 -> 실제 잡힌 양 라인 누적, 상태는 SUBMITTED 유지")
    void reserveLine_accumulatesAndStaysSubmitted() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));
        when(inventoryPort.reserveFromWarehouse("req-1", "SO-1", "OIL-FLT-001", "WH-HQ-001", 10))
                .thenReturn(new ReservationResult("OIL-FLT-001", 10, 7)); // 7만 잡힘

        service.reserveLine(new ReserveLineCommand("SO-1", "OIL-FLT-001", "WH-HQ-001", 10, "req-1"));

        assertThat(so.lines().get(0).reservedQuantity()).isEqualTo(7);
        assertThat(so.status()).isEqualTo(SalesOrderStatus.SUBMITTED); // 예약은 전이가 아님
    }

    @Test
    @DisplayName("reserveLine: 예약 불가 상태면 inventory 예약 호출 안 함(고아 홀드 방지)")
    void reserveLine_invalidState_noInventoryCall() {
        SalesOrder requested = requestedOrder("OIL-FLT-001", 10); // REQUESTED → 예약 불가
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> service.reserveLine(
                new ReserveLineCommand("SO-1", "OIL-FLT-001", "WH-HQ-001", 10, "req-x")))
                .isInstanceOf(SalesOrderStateException.class);

        verify(inventoryPort, never()).reserveFromWarehouse(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("reserveLine: 요청이 미충족분보다 크면 shortfall 만큼만 inventory에 요청")
    void reserveLine_clampsRequestToShortfall() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        so.reserveLine("OIL-FLT-001", 8); // 사전 8 예약 → 부족 2
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));
        when(inventoryPort.reserveFromWarehouse("req-2", "SO-1", "OIL-FLT-001", "WH-HQ-002", 2)) // 10 요청해도 2만
                .thenReturn(new ReservationResult("OIL-FLT-001", 2, 2));

        service.reserveLine(new ReserveLineCommand("SO-1", "OIL-FLT-001", "WH-HQ-002", 10, "req-2"));

        assertThat(so.lines().get(0).reservedQuantity()).isEqualTo(10);
        verify(inventoryPort).reserveFromWarehouse("req-2", "SO-1", "OIL-FLT-001", "WH-HQ-002", 2);
    }

    @Test
    @DisplayName("reserveLine: 비양수 수량은 로컬 거부 + inventory 호출 안 함")
    void reserveLine_nonPositiveQuantity_rejected() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        assertThatThrownBy(() -> service.reserveLine(
                new ReserveLineCommand("SO-1", "OIL-FLT-001", "WH-HQ-001", 0, "req-0")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(inventoryPort, never()).reserveFromWarehouse(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("reserveLine: 이미 충족된 라인은 거부 + inventory 호출 안 함")
    void reserveLine_fullyReserved_rejects() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        so.reserveLine("OIL-FLT-001", 10); // 전량 예약 → 부족 0
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        assertThatThrownBy(() -> service.reserveLine(
                new ReserveLineCommand("SO-1", "OIL-FLT-001", "WH-HQ-002", 5, "req-3")))
                .isInstanceOf(ApiException.class);

        verify(inventoryPort, never()).reserveFromWarehouse(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("fulfillBackorder: 보충분까지 예약돼 전 라인 full이면 IN_FULFILLMENT")
    void fulfillBackorder_reserved_inFulfillment() {
        SalesOrder so = submitted("RLY-12V-30A-01", 5);
        so.reserveLine("RLY-12V-30A-01", 2); // 부분
        so.confirmByHq("HQ001", NOW);                      // -> BACKORDERED(부족 3)
        so.reserveLine("RLY-12V-30A-01", 3); // 보충분 마저(BACKORDERED 에서)
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        service.fulfillBackorder("SO-1");

        assertThat(so.status()).isEqualTo(SalesOrderStatus.IN_FULFILLMENT);
    }

    @Test
    @DisplayName("fulfillBackorder: 여전히 부족하면 BACKORDERED 유지")
    void fulfillBackorder_stillShort_staysBackordered() {
        SalesOrder so = submitted("RLY-12V-30A-01", 5);
        so.reserveLine("RLY-12V-30A-01", 2);
        so.confirmByHq("HQ001", NOW); // -> BACKORDERED, reserved=2
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        service.fulfillBackorder("SO-1");

        assertThat(so.status()).isEqualTo(SalesOrderStatus.BACKORDERED);
        assertThat(so.lines().get(0).reservedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("receive: IN_FULFILLMENT -> RECEIVED, sales.order.received 발행")
    void receive_inFulfillment_publishesReceived() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        so.reserveLine("OIL-FLT-001", 10); // 전량 예약
        so.confirmByHq("HQ001", NOW);                   // -> IN_FULFILLMENT
        when(currentUserProvider.current()).thenReturn(STAFF);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        service.receive("SO-1");

        assertThat(so.status()).isEqualTo(SalesOrderStatus.RECEIVED);
        verify(eventPublisher).publishReceived("SO-1");
    }

    @Test
    @DisplayName("receive: 이벤트 발행 실패 시 예외 전파(트랜잭션 롤백 → 수령 취소)")
    void receive_publishFails_propagates() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        so.reserveLine("OIL-FLT-001", 10);
        so.confirmByHq("HQ001", NOW);
        when(currentUserProvider.current()).thenReturn(STAFF);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));
        doThrow(new RuntimeException("outbox down"))
                .when(eventPublisher).publishReceived("SO-1");

        assertThatThrownBy(() -> service.receive("SO-1"))
                .isInstanceOf(RuntimeException.class);
    }

    // === #55 P1: 락 우선 불변식(외부효과 전에 lockForUpdate) ===
    @Test
    @DisplayName("approve: lockForUpdate 가 외부 구매통지(requestPurchase)보다 먼저 호출된다(#55 P1)")
    void approve_locksBeforeExternalPurchase() {
        SalesOrder so = submitted("RLY-12V-30A-01", 5); // 예약 0 → 확정 시 부족분 PR 발생
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        service.approve("SO-1");

        InOrder ordered = inOrder(repository, procurementPort);
        ordered.verify(repository).lockForUpdate("SO-1");                                 // 락 먼저
        ordered.verify(procurementPort).requestPurchase(eq("SO-1"), any(), anyList());    // 외부효과 나중
    }

    @Test
    @DisplayName("receive: lockForUpdate 가 출고이벤트 발행(publishReceived)보다 먼저 호출된다(#55 P1)")
    void receive_locksBeforePublish() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        so.reserveLine("OIL-FLT-001", 10);
        so.confirmByHq("HQ001", NOW); // → IN_FULFILLMENT
        when(currentUserProvider.current()).thenReturn(STAFF);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        service.receive("SO-1");

        InOrder ordered = inOrder(repository, eventPublisher);
        ordered.verify(repository).lockForUpdate("SO-1");
        ordered.verify(eventPublisher).publishReceived("SO-1");
    }

    // === 읽기 스코핑 ===
    @Test
    @DisplayName("search: 지점은 본인 창고(이름)로 강제, 전달한 코드필터 무시")
    void search_branch_scopedToOwnWarehouseName() {
        when(currentUserProvider.current()).thenReturn(STAFF);
        when(repository.search(any(), anyInt(), anyInt()))
                .thenReturn(new SalesOrderPage(List.of(), 0L, 0, 20));
        // 지점이 타지점 코드로 필터 시도
        SearchSalesOrderQuery q = new SearchSalesOrderQuery(
                null, null, "WH-BR-999", null, null, null, null, 0, 20
        );

        service.search(q);

        ArgumentCaptor<SalesOrderSearchCriteria> captor = ArgumentCaptor.forClass(SalesOrderSearchCriteria.class);
        verify(repository).search(captor.capture(), anyInt(), anyInt());
        assertThat(captor.getValue().toWarehouseName()).isEqualTo("강남 1지점"); // 본인 창고로 강제
        assertThat(captor.getValue().toWarehouseCode()).isNull(); // 넘긴 WH-BR-999 무시
    }

    @Test
    @DisplayName("search: HQ는 전달한 코드 필터 사용, 이름 스코핑 없음")
    void search_hq_usesCodeFilter() {
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.search(any(), anyInt(), anyInt()))
                .thenReturn(new SalesOrderPage(List.of(), 0L, 0, 20));
        SearchSalesOrderQuery q = new SearchSalesOrderQuery(
                null, null, "WH-BR-002", null, null, null, null, 0, 20
        );

        service.search(q);

        ArgumentCaptor<SalesOrderSearchCriteria> captor = ArgumentCaptor.forClass(SalesOrderSearchCriteria.class);
        verify(repository).search(captor.capture(), anyInt(), anyInt());
        assertThat(captor.getValue().toWarehouseCode()).isEqualTo("WH-BR-002");
        assertThat(captor.getValue().toWarehouseName()).isNull();
    }

    @Test
    @DisplayName("get: 지점이 타지점 SO 상세 조회 -> FORBIDDEN")
    void get_branch_otherWarehouse_forbidden() {
        SalesOrder so = submitted("OIL-FLT-001", 10); // 강남 1지점
        when(currentUserProvider.current()).thenReturn(OTHER_BRANCH);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        assertThatThrownBy(() -> service.get("SO-1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("get: 지점이 본인 창고 SO 상세 조회 OK")
    void get_branch_ownWarehouse_ok() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        when(currentUserProvider.current()).thenReturn(STAFF);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        assertThat(service.get("SO-1").soNumber()).isEqualTo("SO-1");
    }

    @Test
    @DisplayName("get: HQ는 어느 지점 SO든 조회 OK")
    void get_hq_anyWarehouse_ok() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        when(currentUserProvider.current()).thenReturn(HQ);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        assertThat(service.get("SO-1").soNumber()).isEqualTo("SO-1");
    }

    // ===== 쓰기 소유권 =====

    @Test
    @DisplayName("cancel: 타지점 사용자 -> FORBIDDEN, 저장 안 함")
    void cancel_otherBranch_forbidden_noSave() {
        SalesOrder so = submitted("OIL-FLT-001", 10); // 강남
        when(currentUserProvider.current()).thenReturn(OTHER_BRANCH);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        assertThatThrownBy(() -> service.cancel("SO-1"))
                .isInstanceOf(ApiException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("cancel: 본인 창고 지점 사용자 OK")
    void cancel_ownBranch_ok() {
        SalesOrder so = requestedOrder("OIL-FLT-001", 10); // 취소는 REQUESTED 에서만
        when(currentUserProvider.current()).thenReturn(STAFF);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        service.cancel("SO-1");

        assertThat(so.status()).isEqualTo(SalesOrderStatus.CANCELED);
    }

    @Test
    @DisplayName("cancel: ADMIN은 소유권 무관 OK")
    void cancel_admin_bypassesOwnership() {
        SalesOrder so = requestedOrder("OIL-FLT-001", 10); // 취소는 REQUESTED 에서만
        when(currentUserProvider.current()).thenReturn(ADMIN);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        service.cancel("SO-1");

        assertThat(so.status()).isEqualTo(SalesOrderStatus.CANCELED);
    }

    @Test
    @DisplayName("update: 타지점 사용자 -> FORBIDDEN, 저장 안 함")
    void update_otherBranch_forbidden_noSave() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        when(currentUserProvider.current()).thenReturn(OTHER_BRANCH);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));
        UpdateSalesOrderCommand cmd = new UpdateSalesOrderCommand(
                "SO-1", SalesOrderPriority.URGENT, "메모", null);

        assertThatThrownBy(() -> service.update(cmd))
                .isInstanceOf(ApiException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("receive: 타지점 사용자 -> FORBIDDEN, 발행 안 함")
    void receive_otherBranch_forbidden_noPublish() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        when(currentUserProvider.current()).thenReturn(OTHER_BRANCH);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        assertThatThrownBy(() -> service.receive("SO-1"))
                .isInstanceOf(ApiException.class);
        verify(eventPublisher, never()).publishReceived(any());
        verify(repository, never()).lockForUpdate(any()); // 소유권 인가 실패 → 락 미획득(인가가 락 전). CodeRabbit #55.
    }

    // ===== 생성 소유권 (Step 3) =====

    @Test
    @DisplayName("create: 본인 창고 앞으로 생성 OK")
    void create_ownWarehouse_ok() {
        when(currentUserProvider.current()).thenReturn(STAFF);
        when(warehousePort.warehouseName("WH-BR-001")).thenReturn("강남 1지점");
        when(itemPort.resolveProduct("OIL-FLT-001"))
                .thenReturn(new ProductSnapshot("OIL-FLT-001", "오일필터", new BigDecimal("1000"), true));
        when(repository.nextSoNumber()).thenReturn("SO-9");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyGuard.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        CreateSalesOrderCommand cmd = new CreateSalesOrderCommand(
                "WH-BR-001", SalesOrderPriority.NORMAL, "메모",
                List.of(new SalesOrderLineCommand("OIL-FLT-001", 10)), null);

        assertThat(service.create(cmd).soNumber()).isEqualTo("SO-9");
    }

    @Test
    @DisplayName("create: 타 지점 앞으로 생성 -> FORBIDDEN, item 조회·저장 안 함")
    void create_otherWarehouse_forbidden() {
        when(currentUserProvider.current()).thenReturn(STAFF); // STAFF=강남
        when(warehousePort.warehouseName("WH-BR-002")).thenReturn("분당 1지점");
        when(idempotencyGuard.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        CreateSalesOrderCommand cmd = new CreateSalesOrderCommand(
                "WH-BR-002", SalesOrderPriority.NORMAL, null,
                List.of(new SalesOrderLineCommand("OIL-FLT-001", 10)), null);

        assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(ApiException.class);
        verify(itemPort, never()).resolveProduct(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: ADMIN은 어느 창고든 생성 OK")
    void create_admin_anyWarehouse_ok() {
        when(currentUserProvider.current()).thenReturn(ADMIN);
        when(warehousePort.warehouseName("WH-BR-003")).thenReturn("부산 1지점");
        when(itemPort.resolveProduct("OIL-FLT-001"))
                .thenReturn(new ProductSnapshot("OIL-FLT-001", "오일필터", new BigDecimal("1000"), true));
        when(repository.nextSoNumber()).thenReturn("SO-9");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyGuard.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        CreateSalesOrderCommand cmd = new CreateSalesOrderCommand(
                "WH-BR-003", SalesOrderPriority.NORMAL, null,
                List.of(new SalesOrderLineCommand("OIL-FLT-001", 10)), null);

        assertThat(service.create(cmd).soNumber()).isEqualTo("SO-9");
    }

    @Test
    @DisplayName("create 멱등: 같은 Idempotency-Key 재요청이면 기존 출고요청 반환, 채번/저장/기록 안 함")
    void create_idempotentReplay_returnsExisting() {
        when(currentUserProvider.current()).thenReturn(STAFF);
        when(idempotencyGuard.findReplay(IdempotencyGuard.SO_CREATE, "BR003", "key-1"))
                .thenReturn(Optional.of("SO-1"));
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(submitted("OIL-FLT-001", 10)));
        CreateSalesOrderCommand cmd = new CreateSalesOrderCommand(
                "WH-BR-001", SalesOrderPriority.NORMAL, "메모",
                List.of(new SalesOrderLineCommand("OIL-FLT-001", 10)), "key-1");

        assertThat(service.create(cmd).soNumber()).isEqualTo("SO-1");
        verify(repository, never()).nextSoNumber();
        verify(repository, never()).save(any());
        verify(idempotencyGuard, never()).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("create 멱등: 동시 같은 키로 record 충돌 시 409(IDEM001) 전파")
    void create_idempotencyConflict_propagates() {
        when(currentUserProvider.current()).thenReturn(STAFF);
        when(idempotencyGuard.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        when(warehousePort.warehouseName("WH-BR-001")).thenReturn("강남 1지점");
        when(itemPort.resolveProduct("OIL-FLT-001"))
                .thenReturn(new ProductSnapshot("OIL-FLT-001", "오일필터", new BigDecimal("1000"), true));
        when(repository.nextSoNumber()).thenReturn("SO-9");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new ApiException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT))
                .when(idempotencyGuard).record(any(), any(), any(), any());
        CreateSalesOrderCommand cmd = new CreateSalesOrderCommand(
                "WH-BR-001", SalesOrderPriority.NORMAL, "메모",
                List.of(new SalesOrderLineCommand("OIL-FLT-001", 10)), "key-2");

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    @DisplayName("stats(#74): HQ는 전체(scope=null) — 상태카운트 passthrough + 백오더 집계(count/topSku)")
    void stats_hq_aggregates() {
        when(currentUserProvider.current()).thenReturn(HQ);
        Map<SalesOrderStatus, Long> counts = new EnumMap<>(SalesOrderStatus.class);
        counts.put(SalesOrderStatus.SUBMITTED, 2L);
        counts.put(SalesOrderStatus.BACKORDERED, 1L);
        when(repository.countByStatus(null)).thenReturn(counts);
        when(repository.findAllByStatus(SalesOrderStatus.BACKORDERED, null))
                .thenReturn(List.of(submitted("OIL-FLT-001", 10)));

        var r = service.stats();

        assertThat(r.byStatus().get(SalesOrderStatus.SUBMITTED)).isEqualTo(2L);
        assertThat(r.backorder().count()).isEqualTo(1);
        assertThat(r.backorder().topSkus()).hasSize(1);
        assertThat(r.backorder().topSkus().get(0).sku()).isEqualTo("OIL-FLT-001");
        assertThat(r.backorder().topSkus().get(0).totalQuantity()).isEqualTo(10);
        assertThat(r.backorder().maxWaitDays()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("stats(#74): BRANCH는 본인 창고이름으로 스코프 전달")
    void stats_branch_scoped() {
        when(currentUserProvider.current()).thenReturn(STAFF); // 강남 1지점
        when(repository.countByStatus("강남 1지점")).thenReturn(new EnumMap<>(SalesOrderStatus.class));
        when(repository.findAllByStatus(SalesOrderStatus.BACKORDERED, "강남 1지점")).thenReturn(List.of());

        service.stats();

        verify(repository).countByStatus("강남 1지점");
        verify(repository).findAllByStatus(SalesOrderStatus.BACKORDERED, "강남 1지점");
    }

    @Test
    @DisplayName("withdraw: SUBMITTED -> REQUESTED (수정 위해 회수)")
    void withdraw_submitted_toRequested() {
        SalesOrder so = submitted("OIL-FLT-001", 10); // SUBMITTED, 강남 소유
        when(currentUserProvider.current()).thenReturn(STAFF);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        service.withdraw("SO-1");

        assertThat(so.status()).isEqualTo(SalesOrderStatus.REQUESTED);
    }

    @Test
    @DisplayName("withdraw: SUBMITTED 아니면 NOT_WITHDRAWABLE")
    void withdraw_notSubmitted_throws() {
        SalesOrder requested = SalesOrder.request("SO-1", "WH-BR-001", "강남 1지점",
                SalesOrderPriority.NORMAL, null,
                List.of(new SalesOrderLine(1, "OIL-FLT-001", "상품", new BigDecimal("1000"), 10)),
                "BR003", NOW); // REQUESTED
        when(currentUserProvider.current()).thenReturn(STAFF);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> service.withdraw("SO-1"))
                .isInstanceOf(SalesOrderStateException.class);
    }
}
