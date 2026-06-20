# 핸드오프 (inventory): `sales.order.received` 구독 — 수령 시 예약분 출고(issue)

## 0. 요약
- sales가 **수령 확정(receive)** 시 `sales.order.received` 이벤트를 발행한다(트랜잭셔널 아웃박스).
- **inventory가 이 토픽을 구독**해서, 해당 `soNumber`의 **RESERVED 예약분을 출고(issue)** 한다: `onHand -= qty`, `reserved -= qty`, status→`ISSUED`, `stock_movement` OUT.
- 이는 기존 `sales.stock-out-requested`(라인 기반 blind `applyOut`) 컨슈머를 **대체**한다. sales는 더 이상 `sales.stock-out-requested`를 발행하지 않는다.

## 1. 토픽 / 봉투 / 키
- **토픽**: `sales.order.received`
- **Kafka key**: `soNumber` (같은 SO는 같은 파티션 → 순서 보장)
- **payload** (sales 공통 봉투 `SalesOrderEventMessage`):
```json
{ "eventId": "<uuid>", "eventType": "received", "soNumber": "SO-2026-0001", "occurredAt": "2026-06-19T17:40:00Z" }
```
- ⚠️ **라인(sku/창고/수량)을 보내지 않는다.** soNumber만 보낸다 — 의도된 설계(§3 참조).
- at-least-once 전제 → `eventId` 멱등 가드 필수.

## 2. inventory가 해야 할 일 (지금 `onStockOut` 패턴 그대로 + issue 호출)
기존 `StockEventListener.onStockOut`과 **동일한 골격**(claim-first 멱등 + @Transactional)이되, 차감 로직만 "라인 blind applyOut" → "soNumber 기반 reservation issue"로 바꾼다. 이미 있는 `ReservationService.issue(soNumber)`(= `POST /api/v1/stocks/reservations/issue` 본체)를 그대로 호출하면 된다.

```java
// inventory: StockEventListener 에 메서드 추가
@KafkaListener(topics = "sales.order.received", groupId = "stock-group")
@Transactional
public void onSalesOrderReceived(String message) {
    SalesOrderReceived e = objectMapper.readValue(message, SalesOrderReceived.class);
    if (processedEventRepository.claim(e.eventId()) == 0) return;   // 재배달 멱등(기존과 동일)
    reservationService.issue(e.soNumber());                          // = POST /reservations/issue 본체
}

// payload DTO (sales 봉투와 동일 필드)
public record SalesOrderReceived(
        String eventId, String eventType, String soNumber, String occurredAt) {}
```

### 멱등 2중 방어 (둘 다 이미 inventory에 있음)
1. `processedEventRepository.claim(eventId)` — 재배달(at-least-once) 차단. 같은 트랜잭션이라 실패 시 롤백→재처리 가능(기존 onStockOut과 동일).
2. `reservationService.issue(soNumber)` 자체가 **RESERVED 할당만** ISSUED로 전환 → 이미 ISSUED면 skip. (혹시 동기 REST `/issue`와 이벤트가 모두 와도 이중 차감 없음.)

## 3. 왜 라인 안 보내고 soNumber만?
- 예약 모델에서 **"무엇을, 어느 창고에서, 얼마나 뺄지"의 정본은 inventory의 `stock_reservation`(soNumber 키)** 이다. sales가 라인/창고를 다시 실어 보내면 이중 관리 + 예약 기록과 불일치(오차) 위험.
- 그래서 sales는 "이 SO 수령됐다(soNumber)"만 알리고, **무엇을 뺄지는 inventory가 자기 예약기록으로 해석**한다. 기존 blind `onStockOut`(sales가 보낸 라인을 그대로 applyOut)과의 핵심 차이.

## 4. 기존 `sales.stock-out-requested` 컨슈머 — 폐기 권장
- `StockEventListener.onStockOut`(라인 기반 `stockCommandService.outbound`)은 **예약 모델과 불일치**: onHand만 깎고 `reserved`를 안 풀어 `available`이 영구히 어긋난다(예약분이 안 풀림).
- sales는 이제 `sales.stock-out-requested`를 **발행하지 않는다** → 이 리스너는 죽은 채로 남는다. **제거 권장**(당분간 둬도 무해하나 혼선 방지 차원).

## 5. 정합성 / 왜 출고는 이벤트(비동기)여도 안전한가
- **예약(reserve)은 approve/reserveLine 시점에 이미 동기 확정**됨 — `available`을 깎아 다른 주문이 못 가져간다(오버셀 방지는 그때 끝).
- 이 이벤트의 출고(issue)는 **예약분을 `reserved→shipped`로 전환(onHand 차감)** 하는 것뿐 → 새로 경합/오버셀이 생길 수 없다. 따라서 **비동기(이벤트)로 처리해도 안전**(예약 결정과 달리 TOCTOU 없음).
- sales는 이벤트 발행과 동시에 SO를 `RECEIVED`로 전이(아웃박스=같은 커밋). inventory 출고가 일시 지연/유실돼도 → inventory의 **예약 TTL reconcile**(`ReservationReconciler`)이 미출고 RESERVED를 정리하므로 영구 묶임 없음.
- **동기 REST `POST /reservations/issue` 와 택1**: sales 표준 receive 경로는 **이 이벤트만** 사용한다(이중 차감 방지). 동기 issue 엔드포인트는 수동/보정용으로 유지.

## 6. (선택) 출고 완료 echo
- 현재 sales는 receive에서 이미 `RECEIVED` 전이를 끝내므로 **inventory→sales 출고완료 echo는 불필요**. 필요해지면 별도 핸드오프.

## 7. 와이어 규약
- 식별자 `soNumber`(문자열), `eventId`=UUID 멱등키. 브로커 `kafka.inwoohub.com:9092`, consumer group `stock-group`(기존과 동일), value-deserializer=String(JSON 문자열 수동 파싱, 기존 패턴 동일).
