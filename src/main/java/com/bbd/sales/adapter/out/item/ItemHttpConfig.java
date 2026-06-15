package com.bbd.sales.adapter.out.item;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** item 호출용 HTTP 클라이언트 구성. (InventoryHttpConfig 와 구조 동일)*/
@Configuration
public class ItemHttpConfig {

    @Bean
    public ItemApiClient itemApiClient(@Value("${item.base-url:http://localhost:8082}") String baseUrl) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(2000);
        rf.setReadTimeout(2000);

        RestClient restClient = RestClient.builder()
                .requestFactory(rf)
                .baseUrl(baseUrl)
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
        return factory.createClient(ItemApiClient.class);
    }
}
