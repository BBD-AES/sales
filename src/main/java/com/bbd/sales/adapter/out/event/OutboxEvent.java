package com.bbd.sales.adapter.out.event;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 헥사고날에서 위치: 인프라(2차 persistence)
 * 헥사고날에서 OutboxRepository와 함께 "보내야 할 이벤트"를 DB에 내구성 있게 적재
 */
@Entity
@Table(name = "outbox", schema = "bbd")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String aggregateType; // "SalesOrder"
    private String aggregateId; // soNumber (= Kafka 파티션 키)
    private String eventType; // "submitted" ...

    @Column(columnDefinition = "text")
    private String payload; // JSON
    private String eventId; // 멱등 키(UUID)
    private Instant createdAt;
    private boolean sent;
    private Instant sentAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload, String eventId) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.eventId = eventId;
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
}
