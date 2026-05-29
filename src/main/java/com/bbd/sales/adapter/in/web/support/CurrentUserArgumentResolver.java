package com.bbd.sales.adapter.in.web.support;

import com.bbd.sales.global.security.CurrentUser;
import com.bbd.sales.global.security.RoleType;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/**
 * 컨트롤러 파라미터가 CurrentUser 타입이면 헤더에서 만들어 주입한다.
 *
 * 핵심 이득: 헤더 3종(X-Employee-Number/X-Role/X-Warehouse-Code) 파싱이 컨트롤러에서 사라진다.
 *           JWT 도입 시 이 클래스만 토큰 파싱으로 바꾸면 컨트롤러는 손대지 않는다.
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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, header + " 헤더가 필요합니다.");
        }
        return value;
    }

    private RoleType parseRole(String raw) {
        try {
            return RoleType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알 수 없는 역할: " + raw);
        }
    }
}
