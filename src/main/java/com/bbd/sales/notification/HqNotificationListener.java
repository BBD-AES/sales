package com.bbd.sales.notification;

import com.bbd.sales.adapter.out.event.SalesOrderEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 헥사고날에서 위치: in-adapter(Kafka 구동)
 * 필요성: 이벤트를 받아 알림 유스케이스 트리거
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HqNotificationListener {
    private final NotificationRepository notifications;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "sales.order.submitted", groupId = "sales-hq-notification")
    public void onSubmitted(String message) throws Exception {
        /**
         * SalesOrderEventMessage.class = SalesOrderEventMessage라는 타입의 런타임 표현(Class 객체)
         * : "클래스 리터럴"이라고 함. 모든 자바 클래스는 메타데이터를 가지고 있고, 그 메타 데이터객체를 값으로 꺼내는 문법임.
         * Jackson에게 JSON을 읽어서 이 틀(record)에 맞춰서 객체를 찍어내라고 지시함.
         */
        SalesOrderEventMessage ev = objectMapper.readValue(message, SalesOrderEventMessage.class);
        if (notifications.existsByEventId(ev.eventId())) {
            return; // 이미 처리한 이벤트 -> 멱등 무시(재배달 대비)
        }
        notifications.save(new Notification(
                "HQ_MANAGER", ev.soNumber(),
                "출고요청 " + ev.soNumber() + " 본사 검토 대기", ev.eventId()
        ));
        log.info("[notify] HQ 알림 생성 so={}", ev.soNumber());
    }
}
