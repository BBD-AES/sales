package com.bbd.sales.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Spring Data JPA 리포지토리(영속 기술 세부). 포트가 아니라 어댑터 내부 도구. */
public interface SalesOrderJpaRepository
        extends JpaRepository<SalesOrderJpaEntity, String>,
        JpaSpecificationExecutor<SalesOrderJpaEntity> {

    Optional<SalesOrderJpaEntity> findBySoNumber(String soNumber);

    /** 채번용: 해당 연도 prefix(예 'SO-2026-%')의 최대 so_number. 없으면 empty. */
    @Query("select max(s.soNumber) from SalesOrderJpaEntity s where s.soNumber like :pattern")
    Optional<String> findMaxSoNumber(@Param("pattern") String pattern);
}
