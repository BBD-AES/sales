package com.bbd.sales.adapter.out.item;

import com.bbd.sales.application.port.out.ItemPort;
import com.bbd.sales.application.port.out.ProductSnapshot;
import com.bbd.sales.application.port.out.SourcingType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 상품 마스터(item) 아웃바운드 어댑터(임시 스텁).
 * TODO: item 서비스 GET /api/v1/items/{sku} REST 호출로 교체. 이름/단가 조회는 Caffeine 캐시 적용 가능 지점
 */
@Component
public class ItemStubAdapter implements ItemPort {

    /** [데모용] 생산품(MAKE) SKU. 그 외는 BUY. 실 item 연동 시 items.sourcingType으로 대체*/
    private static final Set<String> DEMO_MAKE_SKUS = Set.of("CLT-DSK-MED-01", "CLT-CVR-MED-01");

    @Override
    public ProductSnapshot resolveProduct(String sku) {
        SourcingType sourcingType = DEMO_MAKE_SKUS.contains(sku) ? SourcingType.MAKE : SourcingType.BUY;
        return new ProductSnapshot(sku, "상품-" + sku, BigDecimal.valueOf(1000), sourcingType);
    }
}
