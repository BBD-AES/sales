package com.bbd.sales.application.service;

import com.bbd.sales.application.port.out.IdempotencyPort;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 생성(POST) 멱등성 가드(#71). SO/CO 생성 서비스가 공유한다(평행 중복 최소화).
 * 호출측 트랜잭션 안에서 동작 — find 로 재요청을 걸러내고, record 로 최초 처리만 통과시킨다.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    public static final String CO_CREATE = "CO_CREATE";
    public static final String CO_CLOSE = "CO_CLOSE"; // #10: 종료(=재고차감) 멱등 — 키=coNumber 권장(CO당 close 1회 직렬화)
    public static final String SO_CREATE = "SO_CREATE";
    private static final String UNIQUE_CONSTRAINT = "uk_idempotency_key";

    private final IdempotencyPort port;

    /**
     * 멱등 표준(docs/idempotency-spec.md): 중복은 409 — 원본 응답을 캐시·재생하지 않는다.
     * 이미 처리된 키면 409(IDEM003), 같은 키가 다른 scope/요청자에 쓰였으면 키 오용 409(IDEM002), 키 없으면 no-op(=정상 진행).
     * 클라이언트는 409 를 "이미 처리됨"으로 해석(목록 이동/재조회).
     */
    public void ensureFirst(String scope, String requester, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        port.find(idempotencyKey).ifPresent(r -> {
            if (!r.scope().equals(scope) || !r.requester().equals(requester)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_ALREADY_PROCESSED);
        });
    }

    /**
     * 최초 처리 기록. 키 없으면 no-op. 동시 같은 키면 UNIQUE 충돌 → 409(IDEM001, 트랜잭션 롤백). DB UNIQUE 가 정확성의 최종 보루.
     */
    public void record(String scope, String requester, String idempotencyKey, String resourceNumber) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            port.record(idempotencyKey, scope, requester, resourceNumber);
        } catch (DataIntegrityViolationException e) {
            // uk_idempotency_key UNIQUE 충돌(동시 같은 키)만 409. 그 외 무결성 위반은 진짜 결함 → 가리지 말고 전파.
            if (isDuplicateIdempotencyKey(e)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            throw e;
        }
    }

    private boolean isDuplicateIdempotencyKey(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase().contains(UNIQUE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
