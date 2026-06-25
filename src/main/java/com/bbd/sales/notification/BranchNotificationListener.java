package com.bbd.sales.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 지점(BRANCH) 대상 자가알림 리스너 — {@link HqNotificationListener} 와 대칭이나 대상이 '도착 지점'이다.
 * 지점 알림은 targetRole 에 지점 창고명(이름축)을 담아, inbox 가 caller.warehouseName 으로 스코프 조회한다.
 *
 * <p>HQ 알림과 동일하게 {@code AFTER_COMMIT + REQUIRES_NEW} best-effort: 알림 저장 실패가 이미 커밋된 승인 전이를
 * 롤백시키지 않는다(원칙: 돈/재고만 sync, 알림은 비핵심 read-model). 승인 트랜잭션 롤백 시 발화 안 함(올바름).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BranchNotificationListener {
    private final NotificationRepository notifications;

    /** 출고요청 생성(REQUESTED) → 요청 지점에 '제출 검토 요망' 자가알림(점장 열람). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRequested(SalesOrderRequestedEvent ev) {
        if (ev.branchWarehouseName() == null || ev.branchWarehouseName().isBlank()) {
            log.warn("[notify] REQUESTED 지점명 없음 — 지점 알림 생략 so={}", ev.soNumber());
            return; // 지점 스코프 없으면 보낼 대상이 없음(fail-closed).
        }
        try {
            notifications.save(new Notification(
                    ev.branchWarehouseName(), ev.soNumber(),
                    "출고요청 " + ev.soNumber() + " 작성됨 — 제출 검토 요망", ev.eventId()
            ));
            log.info("[notify] 지점 REQUESTED 알림 생성 so={} branch={}", ev.soNumber(), ev.branchWarehouseName());
        } catch (RuntimeException e) {
            log.warn("[notify] 지점 REQUESTED 알림 생성 실패(무시) so={}", ev.soNumber(), e);
        }
    }

    /** HQ 승인으로 전량 예약(IN_FULFILLMENT) → 도착 지점에 '곧 입고' 자가알림. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInFulfillment(SalesOrderInFulfillmentEvent ev) {
        if (ev.branchWarehouseName() == null || ev.branchWarehouseName().isBlank()) {
            log.warn("[notify] IN_FULFILLMENT 지점명 없음 — 지점 알림 생략 so={}", ev.soNumber());
            return; // 지점 스코프 없으면 보낼 대상이 없음(fail-closed).
        }
        try {
            notifications.save(new Notification(
                    ev.branchWarehouseName(), ev.soNumber(),
                    "출고요청 " + ev.soNumber() + " 본사 승인·예약 완료 — 곧 입고 예정", ev.eventId()
            ));
            log.info("[notify] 지점 IN_FULFILLMENT 알림 생성 so={} branch={}", ev.soNumber(), ev.branchWarehouseName());
        } catch (RuntimeException e) {
            // best-effort: 알림 실패가 '이미 커밋된' 승인에 영향 주지 않도록 삼킨다(비핵심 read-model).
            log.warn("[notify] 지점 알림 생성 실패(무시) so={}", ev.soNumber(), e);
        }
    }
}
