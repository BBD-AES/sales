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
 * 영속성 아웃바운드 어댑터: SalesOrderRepository 포트의 JPA 구현.
 * application 은 이 클래스를 모르고 포트에만 의존(의존성 역전).
 */
@Repository
@RequiredArgsConstructor
public class SalesOrderPersistenceAdapter implements SalesOrderRepository {

    private final SalesOrderJpaRepository jpaRepository;
    private final SalesOrderPersistenceMapper mapper;

    @Override
    public String nextSoNumber() {
        // 시드 포맷에 맞춤: SO-2026-0001 (연도 + 4자리 시퀀스).
        String prefix = "SO-" + Year.now().getValue() + "-";
        int seq = jpaRepository.findMaxSoNumber(prefix + "%")
                .map(max -> Integer.parseInt(max.substring(prefix.length())) + 1)
                .orElse(1);
        // 동시 생성 시 같은 번호가 나올 수 있으나 so_number unique 제약으로 방어.
        // 운영에서는 DB 시퀀스/채번 테이블로 원천 차단 권장(TODO).
        return prefix + String.format("%04d", seq);
    }

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

    @Override
    public Optional<SalesOrder> findBySoNumber(String soNumber) {
        return jpaRepository.findBySoNumber(soNumber).map(mapper::toDomain);
    }

    @Override
    public void lockForUpdate(String soNumber) {
        // FOR UPDATE 로 행을 잠근다(존재 시 최신 커밋 상태를 잠그며 적재). 반환 엔티티는 버리지만 락은 트랜잭션 끝까지 유지.
        // 같은 트랜잭션의 후속 load()→findBySoNumber 가 그 잠긴(최신) 행을 읽어 도메인으로 반환한다(신선도는 FOR UPDATE 가 보장).
        jpaRepository.findBySoNumberForUpdate(soNumber);
    }

    @Override
    public SalesOrderPage search(SalesOrderSearchCriteria criteria, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "requestedAt"));

        Page<SalesOrderJpaEntity> result =
                jpaRepository.findAll(SalesOrderPredicates.from(criteria), pageable);

        List<SalesOrder> content = result.getContent().stream().map(mapper::toDomain).toList();
        return new SalesOrderPage(content, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Override
    public Map<SalesOrderStatus, Long> countByStatus(String warehouseNameScope) {
        Map<SalesOrderStatus, Long> counts = new EnumMap<>(SalesOrderStatus.class);
        for (SalesOrderStatus s : SalesOrderStatus.values()) counts.put(s, 0L); // 빈 상태도 0으로 표기
        for (Object[] row : jpaRepository.countGroupByStatus(warehouseNameScope)) {
            counts.put((SalesOrderStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Override
    public List<SalesOrder> findAllByStatus(SalesOrderStatus status, String warehouseNameScope) {
        return jpaRepository.findAllByStatusScoped(status, warehouseNameScope).stream().map(mapper::toDomain).toList();
    }
}
