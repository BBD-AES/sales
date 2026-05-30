package com.bbd.sales.application.port.out;

/**
 * 아웃바운드 포트: 상품/창고 '표시·스냅샷' 참조 데이터.
 * 이름·단가 조회는 빈번하므로, 구현 어댑터 레벨에서 Caffeine 같은 캐시를 둘 수 있다.
 * (재고 수량 판단이 아니라 '이름/단가 조회 최적화'에만 캐시 사용 — 기존 원칙)
 */
public interface CatalogPort {

    /** 주문 시점 스냅샷 원본(상품명/단가). 없으면 구현이 예외 또는 기본값 처리. */
    ProductSnapshot resolveProduct(String sku);

    /** 창고 코드 -> 표시명. */
    String warehouseName(String warehouseCode);
}
