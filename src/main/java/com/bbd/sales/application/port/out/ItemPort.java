package com.bbd.sales.application.port.out;

/**
 * 아웃바운드 포트: 상품 마스터(item) 서비스 조회.
 * 주문 시점 스냅샷(상품명/단가/조달유형) 원본. 조회가 빈번하므로 구현 어댑터에서 Caffeine 캐시 가능(재고 판단에는 캐시 쓰지 않음)
 */
public interface ItemPort {
    /** 주문 시점 스냅샷 원본(상품명/단가/조달유형). 없으면 구현이 예외 처리. */
    ProductSnapshot resolveProduct(String sku);
}
