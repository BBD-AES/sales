package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.application.port.out.SalesOrderSearchCriteria;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;   // ★ JPA Criteria 아님. BooleanBuilder 가 구현하는 QueryDSL Predicate

/**
 * 검색 조건 -> QueryDSL Predicate(동적 필터). 타입세이프(필드 오타=컴파일 에러).
 */
public class SalesOrderPredicates {

    public SalesOrderPredicates() {
    }

    static Predicate from(SalesOrderSearchCriteria c) {
        QSalesOrderJpaEntity so = QSalesOrderJpaEntity.salesOrderJpaEntity;
        BooleanBuilder where = new BooleanBuilder();
        if (c.status() != null) where.and(so.status.eq(c.status()));
        if (c.priority() != null) where.and(so.priority.eq(c.priority()));
        if (c.toWarehouseCode() != null) where.and(so.toWarehouseCode.eq(c.toWarehouseCode()));
        if (c.requestedBy() != null) where.and(so.requestedBy.eq(c.requestedBy()));
        if (c.from() != null) where.and(so.requestedAt.goe(c.from()));
        if (c.to() != null) where.and(so.requestedAt.loe(c.to()));
        return where;
    }
}
