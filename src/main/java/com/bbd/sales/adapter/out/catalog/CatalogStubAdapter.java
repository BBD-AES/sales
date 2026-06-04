package com.bbd.sales.adapter.out.catalog;

import com.bbd.sales.application.port.out.CatalogPort;
import com.bbd.sales.application.port.out.ProductSnapshot;
import com.bbd.sales.application.port.out.SourcingType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 상품/창고 참조 데이터 아웃바운드 어댑터(임시 스텁).
 * TODO: 상품/창고 마스터 조회로 교체. 이름·단가 조회는 Caffeine 캐시 적용 지점(재고 판단 아님).
 */
@Component
public class CatalogStubAdapter implements CatalogPort {

    /**
     * [데모용] 생산품(MAKE) SKU. 그 외는 BUY(구매품).
     * 실 item 마스터 연동 시 items.sourcing_type 컬럼으로 대체.
     */
    private static final Set<String> DEMO_MAKE_SKUS = Set.of("CLT-DSK-MED-01", "CLT-CVR-MED-01");

    @Override
    public ProductSnapshot resolveProduct(String sku) {
        // 실제로는 상품 마스터에서 현재 상품명/단가/조달유형을 읽어와 스냅샷으로 박제.
        SourcingType sourcingType = DEMO_MAKE_SKUS.contains(sku) ? SourcingType.MAKE : SourcingType.BUY;
        return new ProductSnapshot(sku, "상품-" + sku, BigDecimal.valueOf(1000), sourcingType);
    }

    @Override
    public String warehouseName(String warehouseCode) {
        if (warehouseCode == null) return null;
        return warehouseCode + " 창고";
    }
}
