package com.bbd.sales.application.service;

import com.bbd.sales.application.port.out.IdempotencyPort;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 생성(POST) 멱등성 가드(#71). SO/CO 생성 서비스가 공유한다(평행 중복 최소화).
 * 호출측 트랜잭션 안에서 동작 — find 로 재요청을 걸러내고, record 로 최초 처리만 통과시킨다.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    public static final String CO_CREATE = "CO_CREATE";
    public static final String SO_CREATE = "SO_CREATE";

    private final IdempotencyPort port;

    /**
     * 재요청(replay)이면 그때 만든 자원번호를 반환(호출측이 로드해 원본 응답을 돌려줌). 키 없으면 empty(=정상 진행).
     * 같은 키가 다른 scope/요청자에 쓰였으면 키 오용 → 409.
     */
    public Optional<String> findReplay(String scope, String requester, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return port.find(idempotencyKey).map(r -> {
            if (!r.scope().equals(scope) || !r.requester().equals(requester)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return r.resourceNumber();
        });
    }

    /**
     * 최초 처리 기록. 키 없으면 no-op. 동시 같은 키면 UNIQUE 충돌 → 409(트랜잭션 롤백 → 재시도 시 findReplay 가 원본 회수).
     */
    public void record(String scope, String requester, String idempotencyKey, String resourceNumber) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            port.record(idempotencyKey, scope, requester, resourceNumber);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
    }
}
