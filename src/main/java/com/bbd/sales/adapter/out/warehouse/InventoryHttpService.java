package com.bbd.sales.adapter.out.warehouse;

import com.bbd.sales.adapter.out.warehouse.dto.WarehouseResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

// TODO: 재고 예약/출고(reserve/issue
@HttpExchange
public interface InventoryHttpService {
    @GetExchange("/api/v1/warehouses/{code}")
    WarehouseResponse getByCode(@PathVariable("code") String code);
}
