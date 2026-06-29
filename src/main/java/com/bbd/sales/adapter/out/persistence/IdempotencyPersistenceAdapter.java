package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.application.port.out.IdempotencyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * IdempotencyPort를 JPA로 구현한 영속성 어댑터.
 * 멱등키 기록을 DB에 저장하고 조회한다.
 */
@Repository
@RequiredArgsConstructor
public class IdempotencyPersistenceAdapter implements IdempotencyPort {

    private final IdempotencyKeyJpaRepository jpaRepository;

    /** 멱등키에 해당하는 처리 기록을 DB에서 조회한다. */
    @Override
    public Optional<IdempotencyRecord> find(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey)
                .map(e -> new IdempotencyRecord(e.getIdempotencyKey(), e.getScope(), e.getRequester(), e.getResourceNumber()));
    }

    /** 멱등키 처리 기록을 DB에 저장한다. */
    @Override
    public void record(String idempotencyKey, String scope, String requester, String resourceNumber) {
        // 같은 멱등키가 동시에 저장되면 DB unique 제약으로 중복 처리를 막는다.
        // saveAndFlush로 제약 위반을 즉시 발생시켜 호출 측에서 409로 변환할 수 있게 한다.
        jpaRepository.saveAndFlush(new IdempotencyKeyJpaEntity(idempotencyKey, scope, requester, resourceNumber));
    }
}
