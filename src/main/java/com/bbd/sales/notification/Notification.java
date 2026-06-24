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
    @Column(nullable = false)
    private String targetRole; // "HQ_MANAGER"
    @Column(nullable = false)
    private String soNumber;
    @Column(nullable = false)
    private String message;

    @Column(nullable = false, unique = true)
    private String eventId; // 멱등 가드(중복 소비 차단). NOT NULL이라야 NULL 다중허용 우회를 막음
    private boolean read;
    @Column(nullable = false)
    private Instant createdAt;
    private String category; // FE 알림함 탭 필터용. null=미분류(전체 탭에만).

    /** 알림함 탭 카테고리 — SALES=영업(submit·입고 보충), PROCUREMENT=구매(backorder). HQ 를 나누지 않고 알림 종류로 탭 필터. */
    public static final String CAT_SALES = "SALES";
    public static final String CAT_PROCUREMENT = "PROCUREMENT";

    protected Notification() {
    }

    public Notification(String targetRole, String soNumber, String message, String eventId) {
        this(targetRole, soNumber, message, eventId, null);
    }

    public Notification(String targetRole, String soNumber, String message, String eventId, String category) {
        this.targetRole = targetRole;
        this.soNumber = soNumber;
        this.message = message;
        this.eventId = eventId;
        this.category = category;
        this.read = false;
        this.createdAt = Instant.now();
    }

    /** 알림함에서 항목을 열람(클릭)하면 읽음 처리. read-model 갱신이라 멱등(이미 read 여도 안전)·best-effort. */
    public void markAsRead() {
        this.read = true;
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

    public String getCategory() {
        return category;
    }
}
