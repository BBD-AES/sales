package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.application.port.out.SalesOrderPage;
import com.bbd.sales.application.port.out.SalesOrderRepository;
import com.bbd.sales.application.port.out.SalesOrderSearchCriteria;
import com.bbd.sales.domain.SalesOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 영속성 아웃바운드 어댑터: SalesOrderRepository 포트의 JPA 구현.
 * application 은 이 클래스를 모르고 포트 인터페이스에만 의존한다(의존성 역전).
 */
@Repository
@RequiredArgsConstructor
public class SalesOrderPersistenceAdapter implements SalesOrderRepository {

    private static final DateTimeFormatter SO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SalesOrderJpaRepository jpaRepository;
    private final SalesOrderPersistenceMapper mapper;

    @Override
    public String nextSoNumber() {
        // TODO(운영): 일자별 시퀀스/채번 테이블로 충돌 0 보장. 지금은 날짜+랜덤 4자리(스켈레톤).
        String date = LocalDate.now().format(SO_DATE);
        String suffix = String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
        return "SO" + date + "-" + suffix;
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
    public SalesOrderPage search(SalesOrderSearchCriteria criteria, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "requestedAt"));

        Page<SalesOrderJpaEntity> result =
                jpaRepository.findAll(SalesOrderSpecifications.from(criteria), pageable);

        List<SalesOrder> content = result.getContent().stream()
                .map(mapper::toDomain)
                .toList();
        return new SalesOrderPage(content, result.getTotalElements(), result.getNumber(), result.getSize());
    }
}
