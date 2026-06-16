package com.bbd.sales.adapter.out.item;

import com.bbd.sales.application.port.out.ItemPort;
import com.bbd.sales.application.port.out.ProductSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 상품 마스터(item) 아웃바운드 어댑터(임시 스텁).
 * TODO: item 서비스 GET /api/v1/items/{sku} REST 호출로 교체. 이름/단가 조회는 Caffeine 캐시 적용 가능 지점
 */
@Component
public class ItemStubAdapter implements ItemPort {

    @Override
    public ProductSnapshot resolveProduct(String sku) {
        return new ProductSnapshot(sku, "상품-" + sku, BigDecimal.valueOf(1000)); // active=true (데모)
    }
}
