# 핸드오프 (inventory): 재고 예약 — 수동 멀티창고 할당

## 0. 요약 & 위치
- sales의 HQ 담당자가 SO 라인별로 **창고를 직접 골라 여러 번** 예약한다. 한 라인이 `A창고 3개 + B창고 7개`처럼 **여러 창고에서 조달**될 수 있고, 그 내역(할당)의 **정본은 inventory가 보관**한다.
- inventory가 제공할 것: ① 창고별 가용 조회 ② **원자적 단일창고 예약(예약-실량반환)** ③ 예약 해제 ④ 출고(확정) — 모두 **동기 REST** + `stock_reservation` 테이블 + 미출고 예약 만료 reconcile.
- ⚠️ 이 문서는 이전 reserve 설계(`reserve(soNumber, destination, lines[])` 자동 네트워크 할당)를 **대체**한다. 자동할당 엔진 불필요 — inventory는 "지정 창고에서 원자적으로 예약하고 **실제 잡힌 양**을 돌려주기"만 하면 된다.

## 1. 재고 3-상태 (필수 전제)
| 필드 | 의미 | 변경 시점 |
|---|---|---|
| `onHand` 현재고 | 물리 수량 | **출고(④)** 시 ↓ |
| `reserved` 예약 | SO에 묶인 분 | **예약(②)** ↑, 해제(③)/출고(④) ↓ |
| `available` 가용 = onHand − reserved (− safety) | 약속 가능분 | 예약 시 ↓ |

- **불변식: `available ≥ 0` (즉 `reserved ≤ onHand`). 오버셀 절대 금지.**
- 예약은 `available`만 깎는다(현재고 불변). 현재고는 **출고 시에만** 줄어든다.

## 2. 데이터 모델 (inventory)
- `stock (warehouse_code, sku, ...)`: **`reserved_quantity` 컬럼 추가**(없으면). `available`은 `on_hand − reserved` 파생.
- **`stock_reservation` 신규** (할당 = 멀티소스의 정본):

```text
reservation_id  PK
request_id      UNIQUE   -- 멱등키(sales가 보냄)
so_number       idx
sku
warehouse_code
quantity                 -- 이 창고에서 잡은 수량
status          RESERVED | ISSUED | RELEASED
created_at
expires_at               -- 미출고 만료(reconcile)
```
한 SO 라인(`so_number`+`sku`)에 **행 여러 개** = 여러 창고 조달.
- `stock_movement`: 출고(④) 시 OUT 행(기존 테이블). **예약은 movement 아님**(물리이동 아님).

## 3. 엔드포인트 (context-path `/inventory` 포함, 아래는 컨트롤러 상대경로)

### 3.1 가용 조회 — 표시용 (사람이 보고 창고 고름)
`GET /api/v1/stocks/availability?sku={sku}` (멀티 sku 권장: `?sku=A&sku=B`)
```json
{
  "sku": "OIL-FLT-001",
  "warehouses": [
    {"warehouseCode":"WH-HQ-001","warehouseName":"서울DC","onHand":20,"reserved":17,"available":3},
    {"warehouseCode":"WH-HQ-002","warehouseName":"부산DC","onHand":30,"reserved":0,"available":30}
  ]
}
```
- **표시**일 뿐 결정이 아님 → 약간 stale해도 무해(진짜 결정은 ②의 원자 예약).

### 3.2 예약 — 원자적, 단일창고, "예약-실량반환" (★핵심)
`POST /api/v1/stocks/reservations`
```json
// 요청
{"requestId":"<uuid>","soNumber":"SO-2026-0001","sku":"OIL-FLT-001","warehouseCode":"WH-HQ-001","quantity":10}
// 200 — 찰나에 뺏겨 3개만 잡힌 경우
{"reservationId":"r-123","soNumber":"SO-2026-0001","sku":"OIL-FLT-001","warehouseCode":"WH-HQ-001",
 "requested":10,"reserved":3,"remainingRequested":7,"availableAfter":0}
```
동작(한 트랜잭션):
1. `(warehouse_code, sku)` 행 **잠금**(`FOR UPDATE`).
2. `grant = min(quantity, available)`.
3. `reserved += grant`, `stock_reservation` 행 생성(status=RESERVED).
4. **`reserved`(실제 잡힌 양) 반환.**

- **부분 예약은 정상**(에러 아님): `reserved ≤ requested`, 모자라면 `remainingRequested > 0`. 사람이 그걸 보고 다른 창고로 ②를 다시 호출.
- **멱등**: 같은 `requestId` 재요청 → 같은 결과 반환(이중예약 금지).
- 에러: 잘못된 sku/warehouse = 404, quantity ≤ 0 = 400. **재고부족은 에러 아님**(reserved=0이라도 200).

원자성 SQL(택1):
```sql
-- (A) 비관적 잠금
SELECT on_hand_quantity, reserved_quantity
  FROM stock WHERE warehouse_code=:wh AND sku=:sku FOR UPDATE;
-- 앱에서 grant = min(:qty, on_hand - reserved)
UPDATE stock SET reserved_quantity = reserved_quantity + :grant
  WHERE warehouse_code=:wh AND sku=:sku;
-- (B) 조건부 단일 UPDATE: reserved += LEAST(:qty, on_hand - reserved)
```

### 3.3 해제 — 보상(취소/재소싱)
`POST /api/v1/stocks/reservations/{reservationId}/release`
`POST /api/v1/stocks/reservations/release?soNumber={soNumber}` (SO 전체 일괄)
- 동작: `reserved -= quantity`(available↑), status→RELEASED. **멱등**(이미 RELEASED면 no-op).
- sales가 SO cancel/reject, 또는 잘못 잡은 예약 되돌릴 때.

### 3.4 출고(확정) — 물리 이동 (receive 시)
`POST /api/v1/stocks/reservations/issue` body `{"soNumber":"SO-2026-0001"}`
- 동작: 해당 SO의 **RESERVED 할당 전부**에 대해 한 트랜잭션:
  `onHand -= quantity`, `reserved -= quantity`, status→ISSUED, **`stock_movement` OUT 행** 기록.
- **멱등**: 이미 ISSUED면 skip. 부분 RESERVED만 있으면 그것만 출고.
- 반환: 창고별 출고 내역.
- **(결정필요)** 지점(목적지) 창고 재고를 inventory가 추적하면 = **이체**(출발 onHand↓ + 도착 onHand↑, IN/OUT 두 movement). 아니면 출발 OUT만. → sales가 issue에 `destinationWarehouseCode`를 같이 줄지 정하자.
- ※ 이 동기 issue가 기존 `sales.stock-out-requested`(blind `applyOut`) 컨슈머를 **대체**. blind 차감은 이 모델과 안 맞음(할당 기준 차감 + 멱등 필요).

## 4. 만료 reconcile (필수)
- 미출고 `RESERVED`가 `expires_at` 지나면 자동 RELEASE(`@Scheduled`). SO가 버려져도 재고가 영구히 묶이지 않게. (saga 아님 — 멱등 release + 스케줄 reconcile.)

## 5. sales 측 흐름 (맥락)
1. HQ가 SO 검토 중 3.1로 라인 SKU의 창고별 가용 확인.
2. 창고 고르고 3.2 예약 → "3개 잡힘" 보고 부족분은 다른 창고로 3.2 반복.
3. 라인 충족 or "더는 못 채움 → 나머지 backorder" **사람이 결정**(자동 백오더 아님).
4. 확정(approve) → IN_FULFILLMENT / (명시적 부족분 있으면) BACKORDERED.
5. cancel·reject → 3.3 release. receive → 3.4 issue.
- sales는 라인별 `reservedQuantity`(합)만 추적, **창고별 내역 정본은 inventory**(3.1/할당테이블).

## 6. 와이어 규약 / 인증
- 식별자는 **`soNumber`**(soId 아님), 문자열. `requestId` = UUID 멱등키.
- 서비스간 호출은 JWT(게이트웨이 TokenRelay) — bbd-security-core 도입중(sales #53). 내부 호출 인증 수용 필요.

## 7. 범위 밖(별건)
- 백오더 보충 알림(`inventory.stock-replenished`, 입고 후 sales 통지)은 **별도 항목**(현재 미발행 — 추후 핸드오프).
- 자동 소싱 제안(룰 기반)은 나중에 3.1 위에 얹는 옵션. 지금은 사람이 픽.