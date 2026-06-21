package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.application.port.out.IdempotencyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 멱등성 키 영속 아웃바운드 어댑터: IdempotencyPort 의 JPA 구현(#71).
 */
@Repository
@RequiredArgsConstructor
public class IdempotencyPersistenceAdapter implements IdempotencyPort {

    private final IdempotencyKeyJpaRepository jpaRepository;

    @Override
    public Optional<IdempotencyRecord> find(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey)
                .map(e -> new IdempotencyRecord(e.getIdempotencyKey(), e.getScope(), e.getRequester(), e.getResourceNumber()));
    }

    @Override
    public void record(String idempotencyKey, String scope, String requester, String resourceNumber) {
        // saveAndFlush: UNIQUE 위반을 커밋까지 미루지 않고 호출 스택에서 즉시 surfacing → 호출측(IdempotencyGuard)이 catch 해 409 로 변환.
        jpaRepository.saveAndFlush(new IdempotencyKeyJpaEntity(idempotencyKey, scope, requester, resourceNumber));
    }
}
