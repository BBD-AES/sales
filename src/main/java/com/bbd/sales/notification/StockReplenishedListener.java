package com.bbd.sales.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReplenishedListener {

    private final NotificationRepository notifications;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory.stock-replenished", groupId = "sales-backorder")
    public void onStockReplenished(String message) {
        StockReplenished ev = objectMapper.readValue(message, StockReplenished.class);

        if (ev.soNumber() == null || ev.soNumber().isBlank()) {
            return; // SO 무관 보충 -> 관심 없음
        }
        if (notifications.existsByEventId(ev.eventId())) {
            return; // 멱등(재배달 대비)
        }
        try {
            notifications.save(new Notification(
                    "HQ_MANAGER", ev.soNumber(),
                    "백오더 재고 보충 도착 SO=" + ev.soNumber() + " - 충족(fulfillBackorder 처리하세요",
                    ev.eventId()
            ));
            log.info("[notify] 백오더 보충 알림 so={}", ev.soNumber());
        } catch (DataIntegrityViolationException dup) {
            log.debug("[notify] 중복 이벤트 무시 eventId={}", ev.eventId());
        }
    }
}
