package com.bbd.sales.adapter.out.item;

import com.bbd.sales.adapter.out.item.dto.ItemApiResponse;
import com.bbd.sales.application.port.out.ItemPort;
import com.bbd.sales.application.port.out.ProductSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 상품 마스터(item) 아웃바운드 어댑터 - 동기 REST
 *
 * - 창고명 어댑터와 다르게 "폴백 없음"
 * 상품명/단가/조달유형은 주문의 핵심 데이터(가짜 값을 지어낼 수 없음).
 * 그래서 item 미가용/미존재면 실패를 그대로 전파해 주문 생성을 막는다(빠른 실패).
 * -> 비핵심 표시값(창고명)=폴백, 핵심 주문값(가격)=실패.
 */
@Primary
@Component
@RequiredArgsConstructor
public class ItemRestAdapter implements ItemPort {

    private final ItemApiClient client;

    @Override
    public ProductSnapshot resolveProduct(String sku) {
        ItemApiResponse r = client.getBySku(sku); // 404/연결실패 시 예외 전파(폴백 안 함)
        return new ProductSnapshot(
                r.sku(),
                r.name(),
                BigDecimal.valueOf(r.unitPrice()), // item int(원) -> sales BigDecimal
                r.active()
        );
    }
}
