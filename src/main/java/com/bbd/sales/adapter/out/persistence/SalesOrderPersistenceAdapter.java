package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.application.port.out.SalesOrderPage;
import com.bbd.sales.application.port.out.SalesOrderRepository;
import com.bbd.sales.application.port.out.SalesOrderSearchCriteria;
import com.bbd.sales.domain.SalesOrder;
import com.bbd.sales.domain.SalesOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SalesOrderRepository 포트를 JPA로 구현하는 영속성 어댑터.
 * 애플리케이션 계층은 이 구현체가 아니라 SalesOrderRepository 포트에만 의존한다.
 */
@Repository
@RequiredArgsConstructor
public class SalesOrderPersistenceAdapter implements SalesOrderRepository {

    private final SalesOrderJpaRepository jpaRepository;
    private final SalesOrderPersistenceMapper mapper;

    /** 다음 SO 번호 생성 */
    @Override
    public String nextSoNumber() {
        // 시드 포맷에 맞춤: SO-2026-0001 (연도 + 4자리 시퀀스).
        String prefix = "SO-" + Year.now().getValue() + "-";
        int seq = jpaRepository.findMaxSoNumber(prefix + "%")
                .map(max -> Integer.parseInt(max.substring(prefix.length())) + 1)
                .orElse(1);
        // 동시 생성 시 같은 번호가 나올 수 있으나 so_number unique 제약으로 방어.
        return prefix + String.format("%04d", seq);
    }

    /** 도메인 SalesOrder를 JPA 엔티티로 변환해 저장하고, 다시 도메인 객체로 반환함.*/
    @Override
    public SalesOrder save(SalesOrder so) {
        SalesOrderJpaEntity entity = jpaRepository.findBySoNumber(so.soNumber())
                .map(existing -> {
                    mapper.applyTo(existing, so); // 기존 행 갱신(version 유지 -> 낙관적 락 동작)
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(so));
        SalesOrderJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    /** SO 번호로 주문을 조회해 도메인 객체로 반환한다. */
    @Override
    public Optional<SalesOrder> findBySoNumber(String soNumber) {
        return jpaRepository.findBySoNumber(soNumber).map(mapper::toDomain);
    }

    /** 같은 주문에 대한 동시 상태 전이를 막기 위해 해당 SO 행에 비관적 락을 건다. */
    @Override
    public void lockForUpdate(String soNumber) {
        // FOR UPDATE 로 행을 잠근다(존재 시 최신 커밋 상태를 잠그며 적재). 반환 엔티티는 버리지만 락은 트랜잭션 끝까지 유지.
        // 같은 트랜잭션의 후속 load()→findBySoNumber 가 그 잠긴(최신) 행을 읽어 도메인으로 반환한다(신선도는 FOR UPDATE 가 보장).
        jpaRepository.findBySoNumberForUpdate(soNumber);
    }

    /** 검색 조건과 페이징 조건으로 SO 목록을 조회한다. */
    @Override
    public SalesOrderPage search(SalesOrderSearchCriteria criteria, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        // 페이징 요청 객체
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "requestedAt"));

        // 조회 결과 객체
        Page<SalesOrderJpaEntity> result =
                jpaRepository.findAll(SalesOrderPredicates.from(criteria), pageable);
        // jpa entity 목록 -> application/domain 객체에서 쓰기 위한 SalesOrder 목록으로 변환함
        List<SalesOrder> content = result.getContent().stream().map(mapper::toDomain).toList();
        // Spring Data의 Page 객체 대신 우리 프로젝트의 SalesOrderPage로 감싸서 반환함
        return new SalesOrderPage(content, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    /** 상태별 SO 건수를 집계하고, 없는 상태는 0건으로 채워 반환한다. */
    @Override
    public Map<SalesOrderStatus, Long> countByStatus(String warehouseNameScope) {
        Map<SalesOrderStatus, Long> counts = new EnumMap<>(SalesOrderStatus.class);
        for (SalesOrderStatus s : SalesOrderStatus.values()) counts.put(s, 0L); // 빈 상태도 0으로 표기
        for (Object[] row : jpaRepository.countGroupByStatus(warehouseNameScope)) {
            counts.put((SalesOrderStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** 특정 상태의 SO 목록을 창고 스코프 기준으로 조회한다. */
    @Override
    public List<SalesOrder> findAllByStatus(SalesOrderStatus status, String warehouseNameScope) {
        return jpaRepository.findAllByStatusScoped(status, warehouseNameScope).stream().map(mapper::toDomain).toList();
    }
}
