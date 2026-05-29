package com.bbd.sales.application.port.out;

import com.bbd.sales.domain.SalesOrder;

import java.util.Optional;

/**
 * 아웃바운드(피구동) 포트: 영속성.
 * 도메인 객체(SalesOrder)를 주고받는다 — JPA 엔티티를 노출하지 않는다.
 * 구현은 adapter.out.persistence 가 담당하므로, application 은 DB/JPA 를 전혀 모른다.
 */
public interface SalesOrderRepository {

    /** 출고 요청 번호 발번. 구현(시퀀스/날짜기반 등)은 어댑터 책임. */
    String nextSoNumber();

    SalesOrder save(SalesOrder salesOrder);

    Optional<SalesOrder> findBySoNumber(String soNumber);

    SalesOrderPage search(SalesOrderSearchCriteria criteria, int page, int size);
}
