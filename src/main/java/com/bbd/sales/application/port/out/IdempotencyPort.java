package com.bbd.sales.application.port.out;

import java.util.Optional;

/**
 * 생성(POST) 멱등성 저장소 포트(#71).
 * 클라이언트가 보낸 {@code Idempotency-Key} 로 "이미 처리된 생성"을 식별해 재시도 중복주문을 막는다.
 *  - find   : 재요청(replay) 판별 — 이미 기록된 키면 그때 만든 자원번호를 돌려준다.
 *  - record : 최초 처리 기록 — 같은 키 동시 요청이면 영속 UNIQUE 위반(DataIntegrityViolationException)을 전파한다.
 */
public interface IdempotencyPort {

    Optional<IdempotencyRecord> find(String idempotencyKey);

    /**
     * 키 기록. 같은 키가 이미 있으면 UNIQUE 위반을 즉시(호출 스택에서) 던진다 → 호출측이 409 로 변환.
     */
    void record(String idempotencyKey, String scope, String requester, String resourceNumber);

    /** scope=CO_CREATE/SO_CREATE, requester=employeeNumber, resourceNumber=생성된 CO/SO 번호. */
    record IdempotencyRecord(String idempotencyKey, String scope, String requester, String resourceNumber) {
    }
}
