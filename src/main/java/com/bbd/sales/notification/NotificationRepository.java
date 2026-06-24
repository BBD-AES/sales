package com.bbd.sales.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
     boolean existsByEventId(String eventId);
     List<Notification> findTop100ByTargetRoleAndReadFalseOrderByIdDesc(String targetRole);
     /** 알림함 표시용 — 최근 100건(읽음·안읽음 모두). FE 가 read 플래그로 안읽음/읽음을 구분 표시(읽은 알림은 dim). */
     List<Notification> findTop100ByTargetRoleOrderByIdDesc(String targetRole);
     /** 읽음 처리용 단건 조회 — 호출자 버킷(targetRole) 안에서만 찾아 남의 알림 마킹을 차단(스코프 강제). */
     Optional<Notification> findByIdAndTargetRole(Long id, String targetRole);
}
