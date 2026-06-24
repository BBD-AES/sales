package com.bbd.sales.application.port.out;

import com.bbd.sales.domain.SourcingType;

/**
 * 부족분(백오더) 라인 — procurement 보충요청(PR) 통지용 포트 계약 DTO.
 *
 * inventory 이동용 {@link StockTransferLine}(sku/quantity)과 의도적으로 분리한다:
 * 이쪽은 백오더 라우팅 힌트인 {@code sourcingType}(BUY→발주 / MAKE→작업지시)을 추가로 싣는다.
 * inventory 계약(StockTransferLine)에 sourcingType 을 섞지 않기 위함(컨텍스트별 계약 분리).
 *
 * sourcingType 은 nullable: SO 라인 스냅샷이 없으면 null 로 보내고 procurement 가 item 마스터로 폴백한다.
 */
public record ShortfallLine(
        String sku,
        int quantity,
        SourcingType sourcingType // nullable 라우팅 힌트(권위=item 마스터)
) {
}
