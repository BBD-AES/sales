package com.bbd.sales.notification;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 헥사고날에서 위치: repo, controller와 함께 알림 슬라이스
 * 필요성: HQ가 보는 read-model(알림함)
 */
@Entity
@Table(name = "notification", schema = "bbd")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String targetRole; // "HQ_MANAGER"
    private String soNumber;
    private String message;

    @Column(unique = true)
    private String eventId; // 멱등 가드(중복 소비 차단)
    private boolean read;
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(String targetRole, String soNumber, String message, String eventId) {
        this.targetRole = targetRole;
        this.soNumber = soNumber;
        this.message = message;
        this.eventId = eventId;
        this.read = false;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public String getSoNumber() {
        return soNumber;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
