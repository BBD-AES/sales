package com.bbd.sales.adapter.out.event;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 헥사고날에서 위치: 인프라(2차 persistence)
 * 헥사고날에서 OutboxRepository와 함께 "보내야 할 이벤트"를 DB에 내구성 있게 적재
 */
@Entity
@Table(name = "outbox", schema = "bbd",
        indexes = @Index(name = "idx_outbox_unsent", columnList = "sent, id")) // 폴러 핫패스(미발행분 id순) 인덱스
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String aggregateType; // "SalesOrder"
    private String aggregateId; // soNumber (= Kafka 파티션 키)
    private String eventType; // "received"(출고 통지→inventory) / "PURCHASE_REQUESTED"(구매요청 계약). submit 자가알림은 #65로 in-process 강등→outbox 미적재
    private String topic; // 발행 대상 Kafka 토픽 — 토픽이 여러 개라 폴러가 행만 보고 발행처를 안다(계약서 §6)

    @Column(columnDefinition = "text")
    private String payload; // JSON
    @Column(nullable = false, unique = true)
    private String eventId; // 멱등 키(UUID)
    private Instant createdAt;
    private boolean sent;
    private Instant sentAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload, String eventId, String topic) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.eventId = eventId;
        this.topic = topic;
        this.createdAt = Instant.now();
        this.sent = false;
    }

    public void markSent() {
        this.sent = true;
        this.sentAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public String getTopic() {
        return topic;
    }
}
