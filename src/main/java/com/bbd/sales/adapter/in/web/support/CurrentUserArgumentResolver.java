package com.bbd.sales.adapter.in.web.support;

import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import com.bbd.sales.global.security.CurrentUser;
import com.bbd.sales.global.security.RoleType;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 파라미터가 CurrentUser 타입이면 헤더에서 만들어 주입한다.
 *
 * 헤더 3종 파싱이 컨트롤러에서 사라지고, JWT 전환 시 이 클래스만 토큰 파싱으로 교체하면 된다.
 * 실패는 ResponseStatusException 대신 ApiException(+ErrorCode)으로 던져,
 * 나머지 에러와 동일한 ProblemDetail(코드 포함) 포맷을 유지한다.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        String employeeNumber = required(webRequest, "X-Employee-Number");
        String warehouseCode = webRequest.getHeader("X-Warehouse-Code");
        RoleType role = parseRole(required(webRequest, "X-Role"));
        return new CurrentUser(employeeNumber, role, warehouseCode);
    }

    private String required(NativeWebRequest req, String header) {
        String value = req.getHeader(header);
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.AUTH_HEADER_REQUIRED);
        }
        return value;
    }

    private RoleType parseRole(String raw) {
        try {
            return RoleType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.AUTH_ROLE_INVALID);
        }
    }
}
