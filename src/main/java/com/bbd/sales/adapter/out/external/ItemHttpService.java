package com.bbd.sales.adapter.out.external;

import com.bbd.sales.adapter.out.item.dto.ItemApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/v1/items")
public interface ItemHttpService {

    @GetExchange("/{sku}")
    ItemApiResponse getItem(@PathVariable String sku);

}
