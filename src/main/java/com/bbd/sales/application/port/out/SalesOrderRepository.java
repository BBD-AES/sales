package com.bbd.sales.application.port.out;

import com.bbd.sales.domain.SalesOrder;
import com.bbd.sales.domain.SalesOrderStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 아웃바운드 포트: 영속성.
 * 도메인 객체(SalesOrder)를 주고받는다 — JPA 엔티티를 노출하지 않는다.
 * 구현은 adapter.out.persistence 가 담당하므로, application 은 DB/JPA 를 전혀 모른다.
 */
public interface SalesOrderRepository {

    /** 출고 요청 번호 발번. 구현(시퀀스/날짜기반 등)은 어댑터 책임. */
    String nextSoNumber();

    SalesOrder save(SalesOrder salesOrder);

    Optional<SalesOrder> findBySoNumber(String soNumber);

    /**
     * 같은 주문에 대한 상태전이를 한 번에 하나만 처리하기 위해 주문 행을 비관락으로 잠근다.
     * 누가 먼저 처리될지는 보장하지 않지만, 동시에 approve/receive가 겹쳐
     * 이벤트나 외부 호출이 중복 실행되는 것은 막는다.
     * 트랜잭션 안에서 호출해야 락이 커밋/롤백까지 유지된다.
     */
    void lockForUpdate(String soNumber);

    SalesOrderPage search(SalesOrderSearchCriteria criteria, int page, int size);

    /** 대시보드: 상태별 카운트. scope=창고이름(지점 스코프), null=전체. 모든 상태 0 포함. */
    Map<SalesOrderStatus, Long> countByStatus(String warehouseNameScope);

    /** 대시보드: 특정 상태 주문 전체(라인 포함). scope=창고이름, null=전체. 백오더 분석용. */
    List<SalesOrder> findAllByStatus(SalesOrderStatus status, String warehouseNameScope);
}
