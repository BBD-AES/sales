package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.application.port.out.CustomerOrderSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class CustomerOrderSpecifications {
    public CustomerOrderSpecifications() {
    }

    // JPA Specification(검색조건들을 조립식 블록처럼 조립하는 도구)을 이용한 동적쿼리
    static Specification<CustomerOrderJpaEntity> from(CustomerOrderSearchCriteria c) {
        // root: 검색 대상 테이블
        // query: SQL 전체 설계도(orderBy 등)
        // criteriaBuilder: .equal(), .like()와 같은 조건을 제조하는 도구
        return ((root, query, criteriaBuilder) -> {
            // Predicate: SQL의 where 절에 들어가서 참/거짓을 판단하는 조건식 조각
            List<Predicate> predicates = new ArrayList<>();
            if (c.status() != null) predicates.add(criteriaBuilder.equal(root.get("status"), c.status()));
            if (c.dealerWarehouseCode() != null)
                predicates.add(criteriaBuilder.equal(root.get("dealerWarehouseCode"), c.dealerWarehouseCode()));
            if (c.customerName() != null)
                predicates.add(criteriaBuilder.like(root.get("customerName"), "%" + c.customerName() + "%"));
            if (c.requestedBy() != null)
                predicates.add(criteriaBuilder.equal(root.get("requestedBy"), c.requestedBy()));
            if (c.from() != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("requestedAt"), c.to()));

            // predicates list를 array로 바꿈(cb.and()가 list를 못 읽어서)
            // 규칙으로서 빈 배열(new Predicate[0])을 주고 크기는 자바가 알아서 늘려서 맞춤.(메모리 절약)
            // ex. 검색조건 아무것도 없으면 cb.and()는 아무것도 없는 빈상자를 받고(=조건 없음) 데이터베이스의 모든데이터를 가져옴
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        });
    }
}
