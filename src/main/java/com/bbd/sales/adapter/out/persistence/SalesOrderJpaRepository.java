package com.bbd.sales.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/** Spring Data JPA 리포지토리(영속 기술 세부). 포트가 아니라 어댑터 내부 구현 도구다. */
public interface SalesOrderJpaRepository
        extends JpaRepository<SalesOrderJpaEntity, Long>,
        JpaSpecificationExecutor<SalesOrderJpaEntity> {

    Optional<SalesOrderJpaEntity> findBySoNumber(String soNumber);
}
