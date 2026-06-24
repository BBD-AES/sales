package com.bbd.sales.notification;

import com.bbd.sales.application.port.out.CurrentUserProvider;
import com.bbd.sales.global.security.CurrentUser;
import com.bbd.securitycore.adapter.in.annotation.RequireRole;
import com.bbd.securitycore.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationRepository notifications;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 호출자의 알림 버킷 — HQ/ADMIN=본사("HQ_MANAGER"), BRANCH=자기 지점 창고명(이름축).
     * 지점 사용자에게 창고 스코프가 없으면 null(보낼·읽을 대상 없음, fail-closed).
     */
    private String callerBucket(CurrentUser user) {
        String target = user.isBranchUser() ? user.warehouseName() : "HQ_MANAGER";
        return (target == null || target.isBlank()) ? null : target;
    }

    /**
     * 알림함 — 역할별로 다른 버킷을 본다.
     * HQ/ADMIN = 본사 알림함, BRANCH = 자기 지점 알림함(targetRole=지점 창고명). 스코프 없으면 빈 목록(fail-closed).
     */
    @RequireRole({UserRole.HQ_MANAGER, UserRole.HQ_STAFF,
            UserRole.BRANCH_MANAGER, UserRole.BRANCH_STAFF, UserRole.ADMIN})
    @GetMapping
    public List<Notification> inbox() {
        String bucket = callerBucket(currentUserProvider.current());
        return bucket == null ? List.of()
                : notifications.findTop100ByTargetRoleAndReadFalseOrderByIdDesc(bucket);
    }

    /**
     * 알림 읽음 처리 — 항목 클릭 시 호출. 호출자 버킷 안의 알림만 read=true 로 바꾼다
     * (스코프 쿼리로 남의 알림 마킹 차단). 멱등(이미 read 여도 안전)·best-effort(비핵심 read-model).
     * 없거나 타 버킷이면 no-op(204) — 정보 노출 없이 조용히 무시.
     */
    @RequireRole({UserRole.HQ_MANAGER, UserRole.HQ_STAFF,
            UserRole.BRANCH_MANAGER, UserRole.BRANCH_STAFF, UserRole.ADMIN})
    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id) {
        String bucket = callerBucket(currentUserProvider.current());
        if (bucket == null) {
            return;
        }
        notifications.findByIdAndTargetRole(id, bucket).ifPresent(n -> {
            n.markAsRead();
            notifications.save(n);
        });
    }
}
