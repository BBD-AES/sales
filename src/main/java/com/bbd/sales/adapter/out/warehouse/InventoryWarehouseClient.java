package com.bbd.sales.adapter.out.warehouse;

import com.bbd.sales.adapter.out.warehouse.dto.WarehouseResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * [역할]: inventory 조회 방식을 선언함(어떤 HTTP API를 호출할지)
 * inventory 창고 마스터 HTTP 인터페이스(구현은 HttpServiceProxyFactory 가 런타임 생성).
 * RestTemplate/RestClient를 직접 호출하는 보일러 플레이트를 메서드 시그니처로 대체
 */
@HttpExchange // HTTP 클라이언트임을 표시
public interface InventoryWarehouseClient {
    /**
     * GET {base-url} + 해당 경로
     * GET {inventory.base-url}/api/v1/warehouses/{code} -> WarehouseResponse
     * */
    @GetExchange("/api/v1/warehouses/{code}")
    // 응답 JSON을 자동 역직렬화 정
    WarehouseResponse getByCode(@PathVariable("code") String code);
}
