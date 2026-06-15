package com.bbd.sales.adapter.out.item;

import com.bbd.sales.adapter.out.item.dto.ItemApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * item 상품 마스터 HTTP 인터페이스(구현은 프록시 생성).
 */
@HttpExchange
public interface ItemApiClient {

    @GetExchange("/api/v1/items/{sku}")
    ItemApiResponse getBySku(@PathVariable("sku") String sku);
}
