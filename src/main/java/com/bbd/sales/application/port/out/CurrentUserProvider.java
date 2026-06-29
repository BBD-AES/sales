package com.bbd.sales.application.port.out;

import com.bbd.sales.global.security.CurrentUser;

/**
 * 현재 사용자 조회 포트.
 *
 * 신원은 컨트롤러 파라미터로 받지 않는다. 구현체는 bbd-security-core가 Spring SecurityContext에 적재한
 * JWT의 sub로 user-service UserSnapshot(역할·소속·상태)을 조회한 뒤, sales용 CurrentUser로 변환한다.
 */
public interface CurrentUserProvider {
    CurrentUser current();
}
