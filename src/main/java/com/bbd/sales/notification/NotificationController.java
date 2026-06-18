package com.bbd.sales.notification;

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

    // HQ 알림함. 신원이 필요해지면 CurrentUserProvider 주입(역할 게이트는 @RequireRole 권장).
    @GetMapping
    public List<Notification> inbox() {
        return notifications.findTop100ByTargetRoleAndReadFalseOrderByIdDesc("HQ_MANAGER");
    }
}
