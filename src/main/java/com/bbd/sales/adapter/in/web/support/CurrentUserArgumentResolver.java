package com.bbd.sales.adapter.in.web.support;

import com.bbd.sales.global.security.CurrentUser;
import com.bbd.sales.global.security.RoleType;
import com.bbd.securitycore.application.model.CurrentUserSnapshotResult;
import com.bbd.securitycore.application.port.in.GetCurrentUserSnapshotUseCase;
import com.bbd.securitycore.domain.TenancyType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 파라미터가 CurrentUser 타입이면 JWT 인증 주체(bbd-security-core 스냅샷)에서 만들어 주입한다.
 *
 * 역할 게이트는 @RequireRole(RoleAuthorizationAspect)가 담당하고,
 * 이 리졸버는 "누구인가"(사번/역할/소속창고)를 서비스로 넘겨 감사필드 기록·창고 소유권 검증에 쓰게 한다.
 * 인증 자체(미인증=401)는 Spring Security 리소스서버가 컨트롤러 도달 전에 처리하므로, 여기선 항상 인증된 주체가 있다.
 *
 * 매핑:
 *   role          ← UserRole (상수명 동일) → RoleType 로 변환해 sales 도메인과 보안코어 enum 결합을 끊는다.
 *   warehouseCode ← BRANCH 면 tenancyName(= sales 창고코드 WH-BR-00X 이어야 함), HQ 면 null(단일 창고 없음 = 비스코핑).
 */
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final GetCurrentUserSnapshotUseCase getCurrentUserSnapshot;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        CurrentUserSnapshotResult snapshot = getCurrentUserSnapshot.getCurrentUserSnapshot();
        RoleType role = RoleType.valueOf(snapshot.role().name()); // UserRole ↔ RoleType 상수명 동일
        // 지점: 소속 창고 = tenancyName (소유권 검증 기준). 본사: 단일 창고 없음 → null(authorizeRead 가 isHq 로 통과).
        String warehouseCode = snapshot.tenancyType() == TenancyType.BRANCH
                ? snapshot.tenancyName()
                : null;
        return new CurrentUser(snapshot.employeeNumber(), role, warehouseCode);
    }
}
