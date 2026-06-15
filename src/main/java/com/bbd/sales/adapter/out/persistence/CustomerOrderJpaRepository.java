package com.bbd.sales.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// QuerydslPredicateExecutor : findAll(Predicate, Pageable) 같은 타입세이프 동적 조건 조회 기능 추가
// DB 접근을 실행하는 파일.
public interface CustomerOrderJpaRepository
        extends JpaRepository<CustomerOrderJpaEntity, String>,
        QuerydslPredicateExecutor<CustomerOrderJpaEntity> {

    Optional<CustomerOrderJpaEntity> findByCoNumber(String coNumber);

    @Query("select max(c.coNumber) " +
            "from CustomerOrderJpaEntity c " +
            "where c.coNumber like :pattern")
    Optional<String> findMaxCoNumber(@Param("pattern") String pattern);
}
