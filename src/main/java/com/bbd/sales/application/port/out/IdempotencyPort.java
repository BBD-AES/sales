package com.bbd.sales.application.port.out;

import java.util.Optional;

/**
 * 멱등키 저장소 포트.
 * 같은 Idempotency-Key로 동일 유스케이스가 중복 처리되지 않도록 기록을 조회/저장한다.
 */
public interface IdempotencyPort {

    /** 멱등키로 기존 처리 기록을 조회한다. */
    Optional<IdempotencyRecord> find(String idempotencyKey);

    /** 유스케이스 처리 성공 후 멱등키와 생성된 자원 번호를 기록한다. */
    void record(String idempotencyKey, String scope, String requester, String resourceNumber);

    /** 멱등 처리 기록: 어떤 요청자가 어떤 유스케이스에서 어떤 자원을 만들었는지 저장한다. */
    record IdempotencyRecord(String idempotencyKey, String scope, String requester, String resourceNumber) {
    }
}
