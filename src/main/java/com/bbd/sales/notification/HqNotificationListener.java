package com.bbd.sales.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 헥사고날 위치: in-adapter(in-process 이벤트 구동).
 * #65: submit 자가알림을 Kafka 가 아닌 in-process Spring 이벤트로 수신한다(브로커 비의존).
 *
 * <p><b>핵심 전이와 분리(best-effort)</b>: {@code @TransactionalEventListener(AFTER_COMMIT)} 로 submit 커밋 '이후',
 * {@code REQUIRES_NEW}(새 트랜잭션)에서 알림을 만든다. 알림 저장이 실패해도 이미 커밋된 submit 을 롤백시키지 않는다.
 * (프로젝트 원칙: 돈/재고만 sync, 알림은 비핵심 read-model — 비핵심 부수효과가 핵심 상태전이를 차단하면 안 됨.)
 * submit 트랜잭션이 롤백되면 AFTER_COMMIT 이 발화하지 않아 알림도 안 생긴다(올바름). in-process 1회 발화라 dedup/JSON 불필요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HqNotificationListener {
    private final NotificationRepository notifications;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmitted(SalesOrderSubmittedEvent ev) {
        try {
            notifications.save(new Notification(
                    "HQ_MANAGER", ev.soNumber(),
                    "출고요청 " + ev.soNumber() + " 본사 검토 대기", ev.eventId()
            ));
            log.info("[notify] HQ 알림 생성 so={}", ev.soNumber());
        } catch (RuntimeException e) {
            // best-effort: 알림 실패가 '이미 커밋된' 제출에 영향 주지 않도록 삼킨다(비핵심 read-model).
            log.warn("[notify] HQ 알림 생성 실패(무시) so={}", ev.soNumber(), e);
        }
    }

    /** 백오더(재고 부족) 발생 시 HQ 자가알림. submit 알림과 동일하게 AFTER_COMMIT + REQUIRES_NEW best-effort. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBackordered(SalesOrderBackorderedEvent ev) {
        try {
            notifications.save(new Notification(
                    "HQ_MANAGER", ev.soNumber(),
                    "출고요청 " + ev.soNumber() + " 백오더 — 재고 부족, 충당 대기", ev.eventId()
            ));
            log.info("[notify] HQ 백오더 알림 생성 so={}", ev.soNumber());
        } catch (RuntimeException e) {
            log.warn("[notify] HQ 백오더 알림 생성 실패(무시) so={}", ev.soNumber(), e);
        }
    }
}
