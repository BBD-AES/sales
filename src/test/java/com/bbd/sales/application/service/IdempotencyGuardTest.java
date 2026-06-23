package com.bbd.sales.application.service;

import com.bbd.sales.application.port.out.IdempotencyPort;
import com.bbd.sales.application.port.out.IdempotencyPort.IdempotencyRecord;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 생성 멱등 가드 단위테스트(#71). 포트는 목 — 가드의 null 처리·replay 판별·키 오용·충돌 매핑만 검증.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyGuardTest {

    @Mock IdempotencyPort port;
    @InjectMocks IdempotencyGuard guard;

    @Test
    @DisplayName("ensureFirst: 키 null/blank면 포트 조회 없이 통과(no-op)")
    void ensureFirst_nullOrBlankKey_noop() {
        guard.ensureFirst("CO_CREATE", "BR001", null);
        guard.ensureFirst("CO_CREATE", "BR001", "  ");
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("ensureFirst: 같은 scope/요청자 기존 키면 409(IDEM003 이미 처리됨) — 원본 반환 안 함")
    void ensureFirst_match_alreadyProcessed() {
        when(port.find("k1")).thenReturn(Optional.of(new IdempotencyRecord("k1", "CO_CREATE", "BR001", "CO-2026-0001")));
        assertThatThrownBy(() -> guard.ensureFirst("CO_CREATE", "BR001", "k1"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_KEY_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("ensureFirst: 같은 키가 다른 scope/요청자에 쓰였으면 409(IDEM002)")
    void ensureFirst_reuse_conflict() {
        when(port.find("k1")).thenReturn(Optional.of(new IdempotencyRecord("k1", "SO_CREATE", "BR999", "SO-1")));
        assertThatThrownBy(() -> guard.ensureFirst("CO_CREATE", "BR001", "k1"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    @DisplayName("record: 키 null이면 포트 호출 없음(no-op)")
    void record_nullKey_noop() {
        guard.record("CO_CREATE", "BR001", null, "CO-2026-0001");
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("record: uk_idempotency_key UNIQUE 충돌 → 409(IDEM001)")
    void record_uniqueViolation_mappedToConflict() {
        doThrow(new DataIntegrityViolationException("could not execute statement; constraint [uk_idempotency_key]"))
                .when(port).record("k1", "CO_CREATE", "BR001", "CO-2026-0001");
        assertThatThrownBy(() -> guard.record("CO_CREATE", "BR001", "k1", "CO-2026-0001"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    @DisplayName("record: 무관한 무결성 위반은 IDEM001로 가리지 않고 그대로 전파")
    void record_unrelatedViolation_rethrown() {
        doThrow(new DataIntegrityViolationException("null value in column \"requester\" violates not-null constraint"))
                .when(port).record("k1", "CO_CREATE", "BR001", "CO-2026-0001");
        assertThatThrownBy(() -> guard.record("CO_CREATE", "BR001", "k1", "CO-2026-0001"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
