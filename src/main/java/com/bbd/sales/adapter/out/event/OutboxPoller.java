package com.bbd.sales.adapter.out.event;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 헥사고날에서 위치: 인프라 구동 컴포넌트(@Scheduled)
 * 필요성: outbox->Kafka 중계/비동기/재시도/at-least-once
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxRepository outbox;
    /**
     * 헥사고날에서 위치: out-adapter(브로커행)
     * 필요: 실제 전송
     */
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 2000) // 2초마다 미발행분 비위
    @Transactional
    public void flush() {
        for (OutboxEvent e : outbox.findTop100BySentFalseOrderByIdAsc()) {
            String topic = "sales.order." + e.getEventType(); // -> sales.order.submitted
            try {
                // 키 = soNumber -> 같은 주문은 같은 파티션(순서 보장). .get() 으로 발행 확인 후에만 sent 처리.
                kafkaTemplate.send(topic, e.getAggregateId(), e.getPayload()).get();
                e.markSent(); // 발행처리
            } catch (Exception ex) {
                log.warn("[outbox] 발행 실패 id={} topic={} -> 다음 주기 재시도", e.getId(), topic, ex);
            }
        }
    }
}
