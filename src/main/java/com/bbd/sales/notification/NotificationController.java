package com.bbd.sales.notification;

import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import com.bbd.sales.global.security.CurrentUser;
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

    @GetMapping
    public List<Notification> inbox(CurrentUser currentUser) { // 리졸버가 헤더에서 주입
        if (!currentUser.isHq()) {
            throw new ApiException(ErrorCode.SALES_ORDER_FORBIDDEN_ROLE);
        }
        return notifications.findTop100ByTargetRoleAndReadFalseOrderByIdDesc("HQ_MANAGER");
    }
}
