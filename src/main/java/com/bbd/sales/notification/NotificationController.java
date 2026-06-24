package com.bbd.sales.notification;

import com.bbd.sales.application.port.out.CurrentUserProvider;
import com.bbd.sales.global.security.CurrentUser;
import com.bbd.securitycore.adapter.in.annotation.RequireRole;
import com.bbd.securitycore.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationRepository notifications;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 알림함 — 역할별로 다른 버킷을 본다.
     * HQ/ADMIN = 본사 알림함(targetRole="HQ_MANAGER"), BRANCH = 자기 지점 알림함(targetRole=지점 창고명, 이름축 스코프).
     * 지점 사용자에게 창고 스코프가 없으면 빈 목록(fail-closed) — 남의 지점 알림 노출 방지.
     */
    @RequireRole({UserRole.HQ_MANAGER, UserRole.HQ_STAFF,
            UserRole.BRANCH_MANAGER, UserRole.BRANCH_STAFF, UserRole.ADMIN})
    @GetMapping
    public List<Notification> inbox() {
        CurrentUser user = currentUserProvider.current();
        String target = user.isBranchUser() ? user.warehouseName() : "HQ_MANAGER";
        if (target == null || target.isBlank()) {
            return List.of();
        }
        return notifications.findTop100ByTargetRoleAndReadFalseOrderByIdDesc(target);
    }
}
