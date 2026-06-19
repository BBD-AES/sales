````markdown
# Handoff — Procurement: 생산(MAKE) 흡수 + sales 보충요청 계약 변경

## 0. 보내는 쪽 & 한 줄 요약
나는 **sales** 서비스 팀(BBD-AES ERP, MSA, Spring Boot 4.0.6 / Java 21, 헥사고날).
**생산(production)이 별도 서비스 없이 procurement로 흡수**됨에 따라, 그동안 갈 곳 없던
**MAKE(생산품) 부족분도 이제 procurement로** 옵니다. 새 토픽을 만들지 않고
**기존 `sales.purchase-requested` 이벤트에 nullable `sourcingType` 필드 한 개만 추가**해서
BUY+MAKE를 한 토픽으로 보냅니다. procurement가 라인별로 **BUY→PO / MAKE→생산(작업지시)**
로 분기하면 됩니다. **백오더 한 바퀴(완료→`procurement.stock-in-requested`→sales)는 그대로**입니다.

---

## 1. 무엇이/왜 바뀌나
- 조직에 **생산 서비스가 없습니다.** sales는 지금까지 MAKE 부족분을 `ProductionPort`(로그만 찍는
  스텁, 이벤트 발행 0)로 흘려보내고 있었음 → **행선지가 없던 죽은 경로.**
- 생산이 procurement 소관이 됐으니, sales는 **`ProductionPort`를 제거하고 모든 부족분을
  procurement 채널 하나로** 보냅니다. buy냐 make냐는 **procurement가 결정**합니다(생산을 가진 쪽).
- sales는 라인별 `sourcingType`("BUY"/"MAKE")을 **힌트로** 실어 보냅니다. 권위는 item 마스터.
  null이거나 못 믿겠으면 procurement가 item 마스터로 재해석하면 됩니다.

---

## 2. 너희(procurement)의 현재 상태 (내가 본 것 — 너희 레포로 확인 바람)
- **생산 개념이 0건.** 트리 전체 스캔(workorder|bom|manufactur|production|작업|생산) = 0히트.
  컨텍스트는 `vendor/`, `purchaseorder/` 둘뿐.
- **`sales.purchase-requested` 처리**: `PurchaseRequestedListener`(group `procurement-purchase`)
  → `PurchaseRequestNotificationService.handle()`가 **PENDING `PurchaseRequestNotification`
  한 행만 적재**(inbox). PO 자동 생성 안 함. PR 애그리거트 없음. 멱등은 `processed_event`로 가드.
- **PR→PO는 수동**("B안"): 사람이 `GET /api/v1/purchase-requests`로 보고 `POST /api/v1/purchase-orders`
  로 직접 작성. **알림의 soNumber를 PO로 자동 연결하는 코드 없음**(사람이 재입력).
- **PO 상태 = `DRAFT / RECEIVED / CANCELED` 3개뿐.** `markReceived`(DRAFT→RECEIVED)가 완료 전이.
  PO는 **`vendorCode` 필수**(non-blank) — 즉 PO는 본질적으로 vendor 종속.
- **완료 시 stock-in 발행은 이미 완비**: `PurchaseOrderService.complete()` → `markReceived` →
  `publishStockInRequested()`가 outbox에 적재 → `MessageRelay`(@Scheduled 1s)가
  `procurement.stock-in-requested`로 발행. 이벤트 필드는 `poNumber` + **`soNumber`**(= `PO.soNumber`,
  V7에서 so_id→so_number 리네임됨). key=poNumber.
- **item 연동은 있는데 sourcingType을 버림**: `ItemHttpService` GET `/api/v1/items/{sku}`로
  조회하지만, 로컬 `ItemResponse`/`ItemResult`가 `(sku, partName, unitPrice)`만 받음.
  **item 마스터는 `sourcingType`(MAKE/BUY)을 이미 반환하는데 너희가 매핑을 안 함.**
- ⚠️ **기존 버그**: `PurchaseRequestNotification.markDone()`(PENDING→DONE)가 **아무 데서도 호출 안 됨**
  → PO를 만들어도 알림이 영영 PENDING. (이번에 같이 고칠지 결정 바람.)

---

## 3. 계약 변경 — `sales.purchase-requested`에 nullable 필드 1개 추가

**토픽/eventType/key/envelope 전부 그대로.** Line 레코드에 `sourcingType`만 추가:

```java
// 기존
public record Line(String sku, int quantity) {}
// 변경
public record Line(
    String sku,
    int quantity,
    String sourcingType   // ★ nullable. "BUY"|"MAKE" — 분기 힌트(권위=item 마스터). null=미지정→너희가 해석
) {}
```

- **Line 레벨에 둠**(메시지 레벨 아님): 한 SO에 BUY/MAKE가 섞일 수 있어서. 그래서 sales는
  메시지를 안 쪼개고 한 건으로 보냅니다.
- **String("BUY"/"MAKE")** — 공유 enum/jar 없이 JSON 문자열 운반(계약 규율 유지).
- **하위호환 100%** (그래서 nullable): 구버전 procurement는 모르는 필드 무시
  (`FAIL_ON_UNKNOWN_PROPERTIES` off) → 지금처럼 전 sku를 PENDING 적재. 신버전이 구메시지 읽으면
  `sourcingType=null` → item 마스터 폴백. **배포 순서 무관, 토픽/리스너/그룹 변경 0.**

sales 측 변경(참고): `ProductionPort` 삭제, `reserveAndRoute()`에서 BUY/MAKE 분기 제거하고
부족분 전량을 `requestPurchase` 한 채널로, 라인에 sourcingType 힌트 채워서 발행.

---

## 4. procurement가 해야 할 것

### 4.1 sourcing 인지 (선행)
- 너희 `ItemResult`/`ItemResponse`에 **`sourcingType` 필드 추가**(item 마스터가 이미 줌). 또는
  이벤트의 nullable 힌트를 1차로 쓰고, null이면 item 마스터로 재해석. **권장: 힌트 우선 + 마스터 폴백.**

### 4.2 분기 (핵심)
`PurchaseRequestNotificationService.handle()`에서 **라인별로** 분기(메시지에 BUY/MAKE 혼재 가능):
- **BUY(또는 null→BUY로 해석)** → 지금 그대로: PENDING 알림 → 사람이 vendor PO 작성.
- **MAKE** → **새 생산/작업지시 경로**로. **PO에 PRODUCING 상태를 붙이지 말 것** — PO는
  `vendorCode` 필수라 vendor 종속이고, 생산은 vendor가 없음.

### 4.3 새 생산 애그리거트 (권장 형태)
- `purchaseorder/`와 **형제 컨텍스트**(예: `workorder/`), 자체 상태기계
  (`PLANNED → IN_PRODUCTION → COMPLETED / CANCELED`).
- **원 `soNumber`를 반드시 보존**(PurchaseRequested.soNumber → workOrder.soNumber). PO가
  `PO.soNumber` 들고 있듯이 동일하게.
- 인간 개입 여부(B안처럼 PENDING 생산요청 알림 → 사람이 작업지시 생성 vs 자동 생성)는 **너희 결정**.
  일관성 위해 기존 알림 패턴 미러링 권장.

### 4.4 ★ 완료 시 stock-in 발행 (백오더 루프 불변의 핵심)
생산 완료(작업지시 `markCompleted`, PO `markReceived`와 동형) 시 **반드시 기존 outbox/MessageRelay로
`procurement.stock-in-requested` 발행** — `PurchaseOrderService.publishStockInRequested()`와 동일 경로:
- `StockInRequested.of(eventId, occurredAt, <작업지시번호>, <soNumber>, lines)` —
  **SO 연계 필드 = 원 soNumber.** 토픽 `procurement.stock-in-requested`, eventType `STOCK_IN_REQUESTED` 그대로.
- BUY(PO RECEIVED)든 MAKE(작업지시 COMPLETED)든 **토픽·eventType·DTO·soNumber 값이 동일**하면,
  sales의 `sales-backorder` 컨슈머도, inventory의 stock-in 핸들러도 **변경 0**.

---

## 5. 그대로 유지되는 것 (안심하고 안 건드려도 됨)
- 토픽 `sales.purchase-requested` / `procurement.stock-in-requested` 이름·key.
- 멱등(`processed_event`), outbox+`MessageRelay`, vendor PO 흐름 전체.
- 백오더 한 바퀴: `purchase-requested(BUY|MAKE)` → `PO RECEIVED` **또는** `작업지시 COMPLETED`
  → `stock-in-requested(soNumber)` → sales 백오더 충족.

---

## 6. ⚠️ 먼저 합의해야 할 두 가지
1. **`soId` vs `soNumber` 표기 드리프트**: 공유 계약서 `eda-event-spec.md §3-2`와 sales 컨슈머 예시(§4)는
   **`soId`**라 쓰는데, 너희 live `StockInRequested` 레코드와 V7 마이그레이션은 **`soNumber`**.
   sales는 아직 백오더 컨슈머 미구현이라 **너희가 실제 내보내는 `soNumber`에 맞추겠음.** 다만 계약서
   문구를 `soNumber`로 통일하는 PR을 같이 올리자. (BUY/MAKE가 **같은 필드에 같은 값**(원 soNumber)만
   넣으면 됨 — 안 그러면 한쪽 백오더가 조용히 안 돎.)
2. **`markDone()` 미호출 버그**: PO 작성돼도 PENDING 알림이 안 닫힘. 이번에 같이 고칠지/별도로 할지.
   (새 생산요청 알림에도 같은 마감 처리 필요.)

---

## 7. procurement 결정 사항
- [ ] MAKE를 **수동(알림→사람이 작업지시)** vs **자동 작업지시 생성**? (권장: 기존 B안 미러링)
- [ ] 생산 애그리거트를 `purchaseorder`의 형제 신규 컨텍스트로 — 확인(PO에 상태 추가 ❌).
- [ ] `sourcingType`을 ItemResult/ItemResponse에 추가해서 마스터 폴백을 둘지(권장) vs 힌트만 신뢰.
- [ ] soId/soNumber 표기 합의(§6-1).
- [ ] markDone 버그 처리 시점(§6-2).

---

## 8. 완료 기준(Acceptance)
- [ ] `sales.purchase-requested`의 nullable `sourcingType` 무시/활용 모두 정상(구·신버전 호환).
- [ ] MAKE 라인 → 생산 경로(작업지시 신규 애그리거트), BUY 라인 → 기존 PO 경로로 분기.
- [ ] 작업지시 COMPLETED 시 `procurement.stock-in-requested` 발행, **soNumber = 원 SO 번호**.
- [ ] BUY/MAKE 어느 경로든 stock-in 이벤트의 토픽·eventType·SO연계필드·값이 동일.
- [ ] (합의 시) soId→soNumber 계약서 통일, markDone PENDING→DONE 마감.
````

4개 백틱으로 감쌌으니 안쪽 ```java도 안 깨집니다. `docs/handoff-procurement-production.md`로 저장도 해둘까요?