package com.bbd.sales.adapter.out.inventory;

import com.bbd.sales.adapter.out.inventory.dto.IssueRequest;
import com.bbd.sales.adapter.out.inventory.dto.ReserveRequest;
import com.bbd.sales.adapter.out.inventory.dto.ReserveResponse;
import com.bbd.sales.adapter.out.inventory.dto.StockAvailabilityResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * inventory 재고 예약/출고 클라이언트(동기 REST, JWT 릴레이 자동 - @ImportHttpServices 그룹).
 * base-url = bbd-inventory-service(...:8083/inventory). 경로는 context-path/inventory 뒤.
 */
@HttpExchange("/api/v1/stocks")
public interface InventoryStockHttpService {

    // 가용 조회(창고 후보). inventory 는 멀티 sku(List) → List 응답. 단건은 sku 1개짜리 리스트로 호출.
    @GetExchange("/availability")
    List<StockAvailabilityResponse> availability(@RequestParam("sku") List<String> sku);

    // 원자적 단일창고 예약 - 실제 잡힌 양 반환.
    @PostExchange("/reservations")
    ReserveResponse reserve(@RequestBody ReserveRequest request);

    // 출고(확정): SO의 RESERVED 전부 차감 + movement OUT. 멱등.
    @PostExchange("/reservations/issue")
    void issue(@RequestBody IssueRequest request);

    // SO 전체 예약 해제(cancel/reject 보상). 멱등.
    @PostExchange("/reservations/release")
    void releaseBySoNumber(@RequestParam("soNumber") String soNumber);
}
