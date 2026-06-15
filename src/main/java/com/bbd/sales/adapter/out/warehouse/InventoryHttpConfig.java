package com.bbd.sales.adapter.out.warehouse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * [역할]: InventoryWarehouseClient의 실제 구현 빈을 만들어 등록.
 * 빈 생성 시 네트워크 호출을 하지 않으므로 inventory가 안 떠있어도 컨텍스트는 정상 기동.
 */
@Configuration
public class InventoryHttpConfig {

    @Bean
    public InventoryWarehouseClient inventoryWarehouseClient(
            @Value("${inventory.base-url}") String baseUrl
    ) {
        // RestClient가 실제 HTTP 연결을 만들 때 사용할 요청 설정
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(2000); // 연결 2초 - inventory 죽었으면 빨리 실패시켜 폴백
        rf.setReadTimeout(2000); // 응답 2초 - 매달려서 주문 흐름 막는 것 방지

        // RestClient: 어디로, 어떤 timeout/설정으로 HTTP 호출할지를 가진 실행 엔진
        RestClient restClient = RestClient.builder()
                .requestFactory(rf)
                .baseUrl(baseUrl)
                .build();

        // 인터페이스를 실제 구현체(프록시)로 바꾸는 공장
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
        // 인터페이스 -> 동작하는 구현 생
        return factory.createClient(InventoryWarehouseClient.class);
    }
}
