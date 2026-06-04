package com.bbd.sales.application.service;

import com.bbd.sales.application.port.out.*;
import com.bbd.sales.domain.*;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.security.CurrentUser;
import com.bbd.sales.global.security.RoleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOrderService 유스케이스 단위테스트.
 * out 포트를 Mockito 목으로 두고 오케스트레이션 분기(전량예약 / 부족분 BUY·MAKE / 권한·상태 가드)를 검증한다.
 * DB·스프링 컨텍스트 없이 순수 협력 검증.
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderServiceTest {

    @Mock SalesOrderRepository repository;
    @Mock InventoryPort inventoryPort;
    @Mock SalesOrderEventPublisher eventPublisher;
    @Mock CatalogPort catalogPort;
    @Mock ProcurementPort procurementPort;
    @Mock ProductionPort productionPort;

    @InjectMocks SalesOrderService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 3, 10, 0);
    private static final CurrentUser HQ = new CurrentUser("HQ001", RoleType.HQ_MANAGER, null);
    private static final CurrentUser STAFF = new CurrentUser("BR003", RoleType.BRANCH_STAFF, "WH-BR-001");

    /** SUBMITTED 상태의 단일 라인 출고요청. */
    private SalesOrder submitted(String sku, int qty) {
        SalesOrder so = SalesOrder.request("SO-1", "WH-BR-001", "강남 1지점",
                SalesOrderPriority.NORMAL, null,
                List.of(new SalesOrderLine(1, sku, "상품", new BigDecimal("1000"), qty)),
                "BR003", NOW);
        so.submit(NOW);
        return so;
    }

    @Test
    @DisplayName("approve: 전량 가용 -> IN_FULFILLMENT, 생산/구매 호출 없음")
    void approve_allReserved_inFulfillment() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));
        when(inventoryPort.reserve(eq("SO-1"), eq("WH-BR-001"), anyList()))
                .thenReturn(List.of(new ReservationResult("OIL-FLT-001", 10, 10)));

        service.approve("SO-1", HQ);

        assertThat(so.status()).isEqualTo(SalesOrderStatus.IN_FULFILLMENT);
        verify(eventPublisher).publishFulfilling("SO-1");
        verify(procurementPort, never()).requestPurchase(any(), any(), anyList());
        verify(productionPort, never()).requestProduction(any(), any(), anyList());
    }

    @Test
    @DisplayName("approve: 부족분 BUY -> BACKORDERED + 구매요청(PR), 생산 호출 없음")
    void approve_shortfallBuy_requestsPurchase() {
        SalesOrder so = submitted("RLY-12V-30A-01", 5);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));
        when(inventoryPort.reserve(any(), any(), anyList()))
                .thenReturn(List.of(new ReservationResult("RLY-12V-30A-01", 5, 0)));
        when(catalogPort.resolveProduct("RLY-12V-30A-01"))
                .thenReturn(new ProductSnapshot("RLY-12V-30A-01", "릴레이", new BigDecimal("8500"), SourcingType.BUY));

        service.approve("SO-1", HQ);

        assertThat(so.status()).isEqualTo(SalesOrderStatus.BACKORDERED);
        verify(procurementPort).requestPurchase(eq("SO-1"), eq("WH-BR-001"), anyList());
        verify(productionPort, never()).requestProduction(any(), any(), anyList());
        verify(eventPublisher).publishBackordered("SO-1");
    }

    @Test
    @DisplayName("approve: 부족분 MAKE -> BACKORDERED + 생산요청, 구매 호출 없음")
    void approve_shortfallMake_requestsProduction() {
        SalesOrder so = submitted("CLT-DSK-MED-01", 3);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));
        when(inventoryPort.reserve(any(), any(), anyList()))
                .thenReturn(List.of(new ReservationResult("CLT-DSK-MED-01", 3, 1)));
        when(catalogPort.resolveProduct("CLT-DSK-MED-01"))
                .thenReturn(new ProductSnapshot("CLT-DSK-MED-01", "클러치", new BigDecimal("145000"), SourcingType.MAKE));

        service.approve("SO-1", HQ);

        assertThat(so.status()).isEqualTo(SalesOrderStatus.BACKORDERED);
        verify(productionPort).requestProduction(eq("SO-1"), eq("WH-BR-001"), anyList());
        verify(procurementPort, never()).requestPurchase(any(), any(), anyList());
    }

    @Test
    @DisplayName("approve: HQ 권한 아니면 거부, 재고 예약 호출 안 함")
    void approve_nonHqRole_forbidden() {
        SalesOrder so = submitted("OIL-FLT-001", 10);
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));

        assertThatThrownBy(() -> service.approve("SO-1", STAFF))
                .isInstanceOf(ApiException.class);

        verify(inventoryPort, never()).reserve(any(), any(), anyList());
    }

    @Test
    @DisplayName("approve: SUBMITTED 아니면 NOT_DECIDABLE, 재고 예약 호출 안 함")
    void approve_notSubmitted_throws_noReserve() {
        SalesOrder requested = SalesOrder.request("SO-1", "WH-BR-001", "강남 1지점",
                SalesOrderPriority.NORMAL, null,
                List.of(new SalesOrderLine(1, "OIL-FLT-001", "상품", new BigDecimal("1000"), 10)),
                "BR003", NOW); // REQUESTED(미제출)
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> service.approve("SO-1", HQ))
                .isInstanceOf(SalesOrderStateException.class);

        verify(inventoryPort, never()).reserve(any(), any(), anyList());
    }

    @Test
    @DisplayName("fulfillBackorder: 전량 재예약되면 IN_FULFILLMENT")
    void fulfillBackorder_reserved_inFulfillment() {
        SalesOrder so = submitted("RLY-12V-30A-01", 5);
        so.backorder("HQ001", NOW); // -> BACKORDERED
        when(repository.findBySoNumber("SO-1")).thenReturn(Optional.of(so));
        when(inventoryPort.reserve(any(), any(), anyList()))
                .thenReturn(List.of(new ReservationResult("RLY-12V-30A-01", 5, 5)));

        service.fulfillBackorder("SO-1", HQ);

        assertThat(so.status()).isEqualTo(SalesOrderStatus.IN_FULFILLMENT);
        verify(eventPublisher).publishFulfilling("SO-1");
    }
}
