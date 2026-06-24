package com.bbd.sales.domain;

/**
 * 품목 조달 유형(item 마스터 속성, 주문 시점에 ItemPort 로 조회해 SO 라인에 스냅샷으로 박제).
 *
 * 부족분(백오더) 라우팅 힌트로 사용한다: BUY -> 구매요청(PR, procurement 발주), MAKE -> 생산요청(작업지시).
 * 권위(authority)는 item 마스터다. sales 는 주문 시점 값을 스냅샷으로 들고 있다가
 * {@code sales.purchase-requested} 이벤트에 힌트로 실어 보내고, procurement 가 라인별로 분기한다
 * (BUY -> 발주 요청 알림, MAKE -> 생산 요청 알림). procurement 는 힌트가 null/불신 시 item 마스터로 폴백한다.
 *
 * 도메인에 두는 이유: SalesOrderLine 의 스냅샷 필드(name/price 와 동일 논리)라서
 * 도메인 코어가 자기 데이터를 표현하는 타입으로 소유한다(FulfillmentSource 와 동일 위치).
 */
public enum SourcingType {
    BUY,   // 외부 구매품(vendor 에서 사옴) -> 발주(PO)
    MAKE   // 사내 생산품(부품으로 조립/제조) -> 작업지시(WO)
}
