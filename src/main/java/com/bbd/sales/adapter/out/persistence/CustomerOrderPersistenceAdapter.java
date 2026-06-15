package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.application.port.out.CustomerOrderPage;
import com.bbd.sales.application.port.out.CustomerOrderRepository;
import com.bbd.sales.application.port.out.CustomerOrderSearchCriteria;
import com.bbd.sales.domain.CustomerOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.List;
import java.util.Optional;

/**
 * 수주 영속 아웃바운드 어댑터: CustomerOrderRepository 포트의 JPA 구현.
 * application은 이 클래스를 모르고 포트에만 의존(의존성 역전)
 */
@Repository
@RequiredArgsConstructor
public class CustomerOrderPersistenceAdapter implements CustomerOrderRepository {

    private final CustomerOrderJpaRepository jpaRepository;
    private final CustomerOrderPersistenceMapper mapper;

    @Override
    public String nextCoNumber() {
        // CO-2026-0001 (연도 + 4자리 시퀀스). 동시 생성은 co_number unique 제약으로 방어(운영은 시퀀스 권장 TODO).
        String prefix = "CO-" + Year.now().getValue() + "-";
        int seq = jpaRepository.findMaxCoNumber(prefix + "%")
                .map(max -> Integer.parseInt(max.substring(prefix.length())) + 1)
                .orElse(1);
        return prefix + String.format("%04d", seq);
    }

    @Override
    public CustomerOrder save(CustomerOrder co) {
        // 있으면 기존 행 갱신(version 유지 -> 낙관적 락), 없으면 신규 엔티티
        CustomerOrderJpaEntity entity = jpaRepository.findByCoNumber(co.coNumber())
                .map(existing -> {
                    mapper.applyTo(existing, co);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(co));
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<CustomerOrder> findByCoNumber(String coNumber) {
        return jpaRepository.findByCoNumber(coNumber).map(mapper::toDomain);
    }

    @Override
    public CustomerOrderPage search(CustomerOrderSearchCriteria criteria, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "requestedAt"));

        Page<CustomerOrderJpaEntity> result = jpaRepository.findAll(CustomerOrderPredicates.from(criteria), pageable);

        List<CustomerOrder> content = result.getContent().stream().map(mapper::toDomain).toList();
        return new CustomerOrderPage(content, result.getTotalElements(), result.getNumber(), result.getSize());
    }
}
