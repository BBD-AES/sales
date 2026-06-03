package com.bbd.sales.adapter.out.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * in-process 이벤트 소비자(비핵심 부수효과의 자리).
 * AFTER_COMMIT: SO 트랜잭션이 커밋된 뒤에만 실행 -> 롤백 시 알림/감사가 새지 않는다.
 * 현재는 로그 스텁. 알림/감사/읽기모델 갱신 등을 여기에 붙인다(핵심 경로와 분리).
 */
@Slf4j
@Component
public class SalesOrderEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalesOrderEvent(SalesOrderEvent event) {
        log.info("[event:AFTER_COMMIT] {} so={}", event.getClass().getSimpleName(), event.soNumber());
    }
}
