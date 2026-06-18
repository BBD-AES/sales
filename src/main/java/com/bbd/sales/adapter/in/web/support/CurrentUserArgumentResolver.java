package com.bbd.sales.adapter.in.web.support;

import com.bbd.sales.application.port.out.CurrentUserProvider;
import com.bbd.sales.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 파라미터가 CurrentUser 타입이면 CurrentUserProvider(JWT 스냅샷)에서 주입한다.
 *
 * SalesOrder 는 CurrentUser 파라미터를 없애고 서비스가 직접 provider 를 쓰지만,
 * CustomerOrder/Notification 은 아직 CurrentUser 파라미터를 받으므로 이 리졸버가 필요하다.
 * 매핑 로직은 CurrentUserProviderAdapter 한 곳에만 두고 여기선 위임만 한다(중복 제거).
 */
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final CurrentUserProvider currentUserProvider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        return currentUserProvider.current();
    }
}
