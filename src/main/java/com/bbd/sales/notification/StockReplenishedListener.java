package com.bbd.sales.notification;

import com.bbd.sales.application.port.out.SalesOrderRepository;
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
    private final SalesOrderRepository salesOrders; // 연계 SO 의 도착 지점명 해석(지점 입고 알림 대상)
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory.stock-replenished", groupId = "sales-backorder")
    public void onStockReplenished(String message) {
        StockReplenished ev = objectMapper.readValue(message, StockReplenished.class);

        if (ev.soNumber() == null || ev.soNumber().isBlank()) {
            return; // SO 무관 보충 -> 관심 없음
        }
        if (ev.eventId() == null || ev.eventId().isBlank()) {
            // 멱등 키 없음 = 악성/누락 페이로드. 저장하면 NOT NULL 위반 -> '중복'으로 오인되니 여기서 명시적으로 버린다.
            log.warn("[notify] eventId 없는 stock-replenished 무시(멱등 불가): {}", message);
            return;
        }
        if (notifications.existsByEventId(ev.eventId())) {
            return; // 멱등(재배달 대비)
        }
        try {
            notifications.save(new Notification(
                    "HQ_MANAGER", ev.soNumber(),
                    "백오더 재고 보충 도착 SO=" + ev.soNumber() + " — 충족(fulfillBackorder) 처리하세요",
                    ev.eventId()
            ));
            log.info("[notify] 백오더 보충 알림 so={}", ev.soNumber());
        } catch (DataIntegrityViolationException dup) {
            log.debug("[notify] 중복 이벤트 무시 eventId={}", ev.eventId());
        }
        // 도착 지점에도 입고 알림(targetRole=지점 창고명, 이름축). eventId 는 ":branch" 접미사로 HQ 행과 구분(UNIQUE).
        // 상단 existsByEventId(ev.eventId()) 가드가 재배달 시 HQ·지점 둘 다 막아 멱등 보장.
        salesOrders.findBySoNumber(ev.soNumber()).ifPresent(so -> {
            if (so.toWarehouseName() == null || so.toWarehouseName().isBlank()) {
                return;
            }
            try {
                notifications.save(new Notification(
                        so.toWarehouseName(), ev.soNumber(),
                        "출고요청 " + ev.soNumber() + " 재고 보충 도착 — 입고 진행", ev.eventId() + ":branch"
                ));
                log.info("[notify] 지점 보충 알림 so={} branch={}", ev.soNumber(), so.toWarehouseName());
            } catch (DataIntegrityViolationException dup) {
                log.debug("[notify] 지점 보충 알림 중복 무시 so={}", ev.soNumber());
            }
        });
    }
}
