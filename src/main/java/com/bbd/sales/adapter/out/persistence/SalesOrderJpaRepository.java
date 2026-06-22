package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.domain.SalesOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Spring Data JPA 리포지토리(영속 기술 세부). 포트가 아니라 어댑터 내부 도구. */
public interface SalesOrderJpaRepository
        extends JpaRepository<SalesOrderJpaEntity, String>,
        QuerydslPredicateExecutor<SalesOrderJpaEntity> {

    Optional<SalesOrderJpaEntity> findBySoNumber(String soNumber);

    /**
     * 행 비관적 쓰기락(SELECT ... FOR UPDATE) — 최신 커밋 상태를 잠그고 적재한다(#55 P1).
     * <p>대상: approve/receive/fulfillBackorder 처럼 외부효과가 <b>트랜잭션 아웃박스(빠른 로컬 INSERT)</b>이거나 없는 전이.
     * 같은 주문에 동시 진입해도 한 번에 하나만 진행시켜 낙관락 충돌을 깔끔한 직렬화로 바꾼다.
     * <p>일부러 제외: reserveLine — 외부 'REST' 예약을 락 보유 트랜잭션 안에서 호출하면 락-중-네트워크IO 로
     * 같은 SO 전이를 봉쇄할 수 있어 락을 걸지 않는다(재시도 멱등은 #55 2순위 requestId dedup).
     * <p>주의: 락 대기는 세션 {@code lock_timeout}(application-public.yml) 으로 상한을 둔다(무한대기 방지).
     * 파생쿼리명에 'ForUpdate'를 쓰면 키워드로 파싱되므로 명시 @Query 로 둔다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SalesOrderJpaEntity s where s.soNumber = :soNumber")
    Optional<SalesOrderJpaEntity> findBySoNumberForUpdate(@Param("soNumber") String soNumber);

    /** 채번용: 해당 연도 prefix(예 'SO-2026-%')의 최대 so_number. 없으면 empty. */
    @Query("select max(s.soNumber) from SalesOrderJpaEntity s where s.soNumber like :pattern")
    Optional<String> findMaxSoNumber(@Param("pattern") String pattern);

    /** 대시보드(#74): 상태별 카운트(지점 스코프 — :scope null=전체). 반환 행: [SalesOrderStatus, Long]. */
    @Query("select s.status, count(s) from SalesOrderJpaEntity s where (:scope is null or s.toWarehouseName = :scope) group by s.status")
    List<Object[]> countGroupByStatus(@Param("scope") String scope);

    /** 대시보드(#74): 특정 상태 전체(지점 스코프). 백오더 분석용(라인은 @BatchSize 로 적재). */
    @Query("select s from SalesOrderJpaEntity s where s.status = :status and (:scope is null or s.toWarehouseName = :scope)")
    List<SalesOrderJpaEntity> findAllByStatusScoped(@Param("status") SalesOrderStatus status, @Param("scope") String scope);
}
