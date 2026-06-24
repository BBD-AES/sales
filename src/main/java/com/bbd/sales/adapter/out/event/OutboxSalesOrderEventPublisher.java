package com.bbd.sales.adapter.out.event;

import com.bbd.sales.application.port.out.SalesOrderEventPublisher;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import com.bbd.sales.notification.SalesOrderBackorderedEvent;
import com.bbd.sales.notification.SalesOrderSubmittedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * SalesOrderEventPublisher 포트 구현.
 *  - publishReceived → 교차서비스(→inventory) 이벤트라 Kafka outbox 에 적재(트랜잭셔널 아웃박스, at-least-once).
 *  - publishSubmitted → #65: 자가알림(sales→sales, 타 서비스 미구독)이라 Kafka 가 아닌 in-process Spring 이벤트로 발행한다.
 *    HqNotificationListener(@TransactionalEventListener AFTER_COMMIT)가 submit 커밋 후 best-effort 로 알림을 생성한다 →
 *    브로커 비의존 + 핵심 전이 비차단(알림 실패가 submit 을 롤백시키지 않음).
 *    원칙: 교차서비스=Kafka(EDA), 서비스 내부=in-process, 알림은 비핵심 read-model(best-effort).
 */
@Component
@RequiredArgsConstructor
public class OutboxSalesOrderEventPublisher implements SalesOrderEventPublisher {

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper; // Boot 자동 구성 빈
    private final ApplicationEventPublisher events; // #65: 자가알림 in-process 발행

    @Override
    public void publishSubmitted(String soNumber) {
        // #65: 내부 전용 알림 → Kafka outbox 대신 in-process 이벤트. 리스너가 submit 커밋 후(AFTER_COMMIT) best-effort 로 생성(핵심 전이 비차단).
        events.publishEvent(new SalesOrderSubmittedEvent(soNumber, UUID.randomUUID().toString()));
    }

    @Override
    public void publishBackordered(String soNumber) {
        // submit 알림과 동일 패턴: 내부 전용 in-process 이벤트. 리스너가 AFTER_COMMIT best-effort 로 HQ 알림 생성(핵심 전이 비차단).
        events.publishEvent(new SalesOrderBackorderedEvent(soNumber, UUID.randomUUID().toString()));
    }

    @Override
    public void publishReceived(String soNumber) {
        enqueue("received", soNumber); // 토픽 sales.order.received, key=soNumber → inventory가 구독해 issue
    }

    private void enqueue(String eventType, String soNumber) {
        String eventId = UUID.randomUUID().toString();
        var msg = new SalesOrderEventMessage(eventId, eventType, soNumber, Instant.now().toString());
        // try-catch 하지 않아도 됨. 컴파일러가 예외를 강제하지 않는 Jackson 3 Json 3 JacksonException 이므로.
        // 실패하면 unchecked 예외가 그대로 위로 전파 -> submit()의 @Transactional이 롤백 -> GlobalExceptionHandler가 처리(기본 500)
        // 오직 에러코드만 명확히 하고 싶어서 ApiException으로 감싸서 던짐.
        String payload;
        try {
            payload = objectMapper.writeValueAsString(msg);
        } catch (JacksonException e) {
            throw new ApiException(ErrorCode.EVENT_SERIALIZATION_FAILED);
        }

        // 이 save()는 submit()의 @Transactional 안에서 실행 -> SO 저장과 같은 커밋(원자적, dual-write 회피)
        // 기존 SalesOrder 알림 토픽 규칙 유지: sales.order.<eventType> (계약 외 내부 채널)
        outbox.save(new OutboxEvent("SalesOrder", soNumber, eventType, payload, eventId, "sales.order." + eventType));
    }
}
