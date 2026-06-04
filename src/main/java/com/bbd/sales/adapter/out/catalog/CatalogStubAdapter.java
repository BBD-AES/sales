package com.bbd.sales.adapter.out.catalog;

import com.bbd.sales.application.port.out.CatalogPort;
import com.bbd.sales.application.port.out.ProductSnapshot;
import com.bbd.sales.application.port.out.SourcingType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 상품/창고 참조 데이터 아웃바운드 어댑터(임시 스텁).
 * TODO: 상품/창고 마스터 조회로 교체. 이름·단가 조회는 Caffeine 캐시 적용 지점(재고 판단 아님).
 */
@Component
public class CatalogStubAdapter implements CatalogPort {

    @Override
    public ProductSnapshot resolveProduct(String sku) {
        // 실제로는 상품 마스터에서 현재 상품명/단가/조달유형을 읽어와 스냅샷으로 박제.
        // 스텁: 기본 BUY(구매품). 생산품(MAKE) 시연은 마스터 연동/시드 성형 시 분기.
        return new ProductSnapshot(sku, "상품-" + sku, BigDecimal.valueOf(1000), SourcingType.BUY);
    }

    @Override
    public String warehouseName(String warehouseCode) {
        if (warehouseCode == null) return null;
        return warehouseCode + " 창고";
    }
}
