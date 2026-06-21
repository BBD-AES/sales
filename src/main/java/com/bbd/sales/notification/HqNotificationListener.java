package com.bbd.sales.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 헥사고날 위치: in-adapter(in-process 이벤트 구동).
 * #65: submit 자가알림을 Kafka 가 아닌 in-process Spring 이벤트로 수신한다(브로커 비의존).
 * {@code @EventListener}(동기)라 submit()의 @Transactional 안에서 실행 → 알림이 '제출과 원자적으로' 커밋된다
 * (알림 저장 실패 시 submit 도 롤백). in-process 는 1회 발행이라 JSON 역직렬화·eventId 멱등 dedup 이 불필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HqNotificationListener {
    private final NotificationRepository notifications;

    @EventListener
    public void onSubmitted(SalesOrderSubmittedEvent ev) {
        notifications.save(new Notification(
                "HQ_MANAGER", ev.soNumber(),
                "출고요청 " + ev.soNumber() + " 본사 검토 대기", ev.eventId()
        ));
        log.info("[notify] HQ 알림 생성 so={}", ev.soNumber());
    }
}
