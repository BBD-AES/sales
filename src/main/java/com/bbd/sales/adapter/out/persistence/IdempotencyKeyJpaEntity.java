package com.bbd.sales.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 생성 멱등성 키 기록(#71). idempotency_key UNIQUE 가 동시 같은 키의 이중 생성을 막는 단 하나의 선.
 * resource_number = 그 키로 최초 생성된 CO/SO 번호(재요청 시 이 자원을 그대로 반환).
 */
@Entity
@Table(name = "idempotency_key",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKeyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Column(nullable = false, length = 40)
    private String scope;          // CO_CREATE / SO_CREATE

    @Column(nullable = false, length = 50)
    private String requester;      // employeeNumber

    @Column(name = "resource_number", nullable = false, length = 50)
    private String resourceNumber; // 생성된 CO/SO 번호

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public IdempotencyKeyJpaEntity(String idempotencyKey, String scope, String requester, String resourceNumber) {
        this.idempotencyKey = idempotencyKey;
        this.scope = scope;
        this.requester = requester;
        this.resourceNumber = resourceNumber;
        this.createdAt = LocalDateTime.now();
    }
}
