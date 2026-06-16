package com.bbd.sales.adapter.out.event;

import com.bbd.sales.application.port.out.SalesOrderEventPublisher;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * 헥사고날에서 필요성: 포트를 "outbox 테이블에 같은 트랜잭션으로 INSERT"하게 구현
 */
@Component
@RequiredArgsConstructor
public class OutboxSalesOrderEventPublisher implements SalesOrderEventPublisher {

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper; // Boot 자동 구성 빈

    @Override
    public void publishRequested(String soNumber) {
        enqueue("requested", soNumber);
    }

    @Override
    public void publishUpdated(String soNumber) {
        enqueue("updated", soNumber);
    }

    @Override
    public void publishSubmitted(String soNumber) {
        enqueue("submitted", soNumber);
    }

    @Override
    public void publishCanceled(String soNumber) {
        enqueue("canceled", soNumber);
    }

    @Override
    public void publishFulfilling(String soNumber) {
        enqueue("fulfilling", soNumber);
    }

    @Override
    public void publishBackordered(String soNumber) {
        enqueue("backordered", soNumber);
    }

    @Override
    public void publishRejected(String soNumber) {
        enqueue("rejected", soNumber);
    }

    @Override
    public void publishReceived(String soNumber) {
        enqueue("received", soNumber);
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
