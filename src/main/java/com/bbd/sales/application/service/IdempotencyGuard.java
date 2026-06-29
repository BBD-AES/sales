package com.bbd.sales.application.service;

import com.bbd.sales.application.port.out.IdempotencyPort;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 멱등키 기반 중복 실행 방지 컴포넌트.
 * SO/CO 생성처럼 같은 요청이 두 번 처리되면 안 되는 유스케이스에서 사용한다.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    public static final String CO_CREATE = "CO_CREATE";
    public static final String CO_CLOSE = "CO_CLOSE"; // CO 종료 요청 멱등 범위
    public static final String SO_CREATE = "SO_CREATE";
    private static final String UNIQUE_CONSTRAINT = "uk_idempotency_key";

    private final IdempotencyPort port;

    /**
     * 이미 처리된 멱등키인지 확인한다.
     * 같은 키가 이미 있으면 중복 요청으로 보고, 다른 scope/requester에 쓰였으면 키 재사용 오류로 본다.
     */
    public void ensureFirst(String scope, String requester, String idempotencyKey) {
        // 멱등키가 없으면 기존 흐름대로 처리한다.
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
     * 유스케이스 처리 성공 후 멱등키를 기록한다.
     * 동시에 같은 키가 기록되면 DB unique 제약으로 중복 처리를 막는다.
     */
    public void record(String scope, String requester, String idempotencyKey, String resourceNumber) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            port.record(idempotencyKey, scope, requester, resourceNumber);
        } catch (DataIntegrityViolationException e) {
            // 같은 멱등키가 동시에 기록된 경우만 409로 변환한다.
            if (isDuplicateIdempotencyKey(e)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            throw e;
        }
    }

    /** 예외 원인 체인에서 멱등키 unique 제약 위반 여부를 확인한다. */
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
