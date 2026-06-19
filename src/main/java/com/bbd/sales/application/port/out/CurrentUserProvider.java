package com.bbd.sales.application.port.out;

import com.bbd.sales.global.security.CurrentUser;

/** 현재 인증 주체 조회 포트. 구현은 JWT 보안 컨텍스트에서 얻는다(컨트롤러 파라미터 아님). */
public interface CurrentUserProvider {
    CurrentUser current();
}
