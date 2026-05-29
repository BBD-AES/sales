package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.application.port.out.SalesOrderSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** 검색 조건 -> JPA Specification 변환. 동적 필터 조립. */
final class SalesOrderSpecifications {

    private SalesOrderSpecifications() {
    }

    static Specification<SalesOrderJpaEntity> from(SalesOrderSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (c.status() != null)            ps.add(cb.equal(root.get("status"), c.status()));
            if (c.priority() != null)          ps.add(cb.equal(root.get("priority"), c.priority()));
            if (c.fromWarehouseCode() != null) ps.add(cb.equal(root.get("fromWarehouseCode"), c.fromWarehouseCode()));
            if (c.toWarehouseCode() != null)   ps.add(cb.equal(root.get("toWarehouseCode"), c.toWarehouseCode()));
            if (c.requestedBy() != null)       ps.add(cb.equal(root.get("requestedBy"), c.requestedBy()));
            if (c.from() != null)              ps.add(cb.greaterThanOrEqualTo(root.get("requestedAt"), c.from()));
            if (c.to() != null)                ps.add(cb.lessThanOrEqualTo(root.get("requestedAt"), c.to()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
