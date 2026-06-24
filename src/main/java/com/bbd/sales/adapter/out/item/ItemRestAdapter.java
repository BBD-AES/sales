package com.bbd.sales.adapter.out.item;

import com.bbd.sales.adapter.out.item.dto.ItemApiResponse;
import com.bbd.sales.application.port.out.ItemPort;
import com.bbd.sales.application.port.out.ProductSnapshot;
import com.bbd.sales.domain.SourcingType;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;

/**
 * 상품 마스터(item) 아웃바운드 어댑터 - 동기 REST
 *
 * - 창고명 어댑터와 다르게 "폴백 없음"
 * 상품명/단가/조달유형은 주문의 핵심 데이터(가짜 값을 지어낼 수 없음).
 * 그래서 item 미가용/미존재면 실패를 그대로 전파해 주문 생성을 막는다(빠른 실패).
 * -> 비핵심 표시값(창고명)=폴백, 핵심 주문값(가격)=실패.
 */
@Component // #40: ItemStubAdapter 제거로 ItemPort 단일 구현 → @Primary 불필요
@RequiredArgsConstructor
public class ItemRestAdapter implements ItemPort {

    private final ItemHttpService client;

    @Override
    public ProductSnapshot resolveProduct(String sku) {
        try {
            ItemApiResponse r = client.getItem(sku); // 404/연결실패 시 예외 전파(폴백 안 함)
            return new ProductSnapshot(
                    r.sku(),
                    r.name(),
                    BigDecimal.valueOf(r.unitPrice()), // item int(원) -> sales BigDecimal
                    r.active(),
                    parseSourcingType(r.sourcingType()) // "BUY"/"MAKE"/null → enum (불명값은 null로 관대 처리)
            );
        } catch (HttpClientErrorException.NotFound e) {
            // item 404 = 존재하지 않는 SKU(클라 입력 오류) -> 4xx 로 번역. 서버 결함(500)으로 뜨지 않도록.
            throw new ApiException(ErrorCode.ITEM_NOT_FOUND, "존재하지 않는 SKU: " + sku);
        }
        // 404 외 4xx(401/403 등)·5xx·연결실패는 그대로 전파(ITEM_NOT_FOUND로 오표기 금지). 주문 생성 실패(빠른 실패).
    }

    /**
     * item 의 sourcingType 문자열을 enum 으로. null/공백/미지의 값이면 null 반환(라우팅 힌트는 best-effort —
     * 알 수 없으면 다운스트림이 item 마스터로 폴백하므로, 여기서 예외로 주문 생성을 막지 않는다).
     */
    private SourcingType parseSourcingType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return SourcingType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; // 계약 밖 값 → 미지정 취급(빠른 실패 대상 아님)
        }
    }
}
