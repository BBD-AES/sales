package com.bbd.sales.notification;

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

    // HQ 알림함 — HQ 직무만 열람.
    @RequireRole({UserRole.HQ_MANAGER, UserRole.HQ_STAFF, UserRole.ADMIN})
    @GetMapping
    public List<Notification> inbox() {
        return notifications.findTop100ByTargetRoleAndReadFalseOrderByIdDesc("HQ_MANAGER");
    }
}
