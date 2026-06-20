package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.application.port.out.CustomerOrderSearchCriteria;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;   // ★ JPA Criteria 아님. BooleanBuilder 가 구현하는 QueryDSL Predicate

import static org.springframework.util.StringUtils.hasText;


public class CustomerOrderPredicates {
    public CustomerOrderPredicates() {
    }

    static Predicate from(CustomerOrderSearchCriteria c) {
        // Q 클래스: 엔티티 클래스의 메타 정보를 담고 있는 프록시 객체로,
        // 자바 코드 기반으로 타입 안정성(Type-Safe)을 보장하는 쿼리를 작성하게 돕는 핵심 역할임


        QCustomerOrderJpaEntity co = QCustomerOrderJpaEntity.customerOrderJpaEntity;
        BooleanBuilder where = new BooleanBuilder();
        if (c.status() != null) where.and(co.status.eq(c.status()));
        if (hasText(c.dealerWarehouseCode())) where.and(co.dealerWarehouseCode.eq(c.dealerWarehouseCode())); // HQ 코드 필터
        if (hasText(c.dealerName())) where.and(co.dealerName.eq(c.dealerName()));                            // 지점 본인지점 강제(이름축)
        if (hasText(c.customerName())) where.and(co.customerName.containsIgnoreCase(c.customerName()));
        if (hasText(c.requestedBy())) where.and(co.requestedBy.eq(c.requestedBy())); // 빈문자열(=)은 미적용(eq("") 방지)
        if (c.from() != null) where.and(co.requestedAt.goe(c.from()));
        if (c.to() != null) where.and(co.requestedAt.loe(c.to()));
        return where;
    }
}
