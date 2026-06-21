# [요청] inventory — 수주(CO) 종료 출고용 REST 엔드포인트 노출

> 작성 2026-06-21 · 요청: sales팀 → 대상: **inventory 서비스팀** · 관련 sales 이슈 #69

## 한 줄
sales가 **수주(CustomerOrder) 종료(close) 시 지점 재고를 동기 차감**하려고 합니다. inventory에 **이미 구현돼 있는** `StockCommandService.outbound(StockOutRequested)`(창고+SKU 차감, 비관락, movement OUT)를 **REST 엔드포인트로 노출**해 주세요. 차감 엔진은 완성돼 있고 **입구(컨슈머/엔드포인트)만 없는 상태**입니다.

## 왜 동기 REST인가
- 지점 재고는 **HQ 예약과 공유 자원**(HQ가 타 지점 SO 충족 위해 그 지점 창고를 예약). 한쪽(예약)은 sync인데 판매를 async로 두면 **오버셀**.
- 부족 시 **종료를 차단**해야 함(재고/돈 = sync 원칙, TOCTOU). 결과를 받아야 하므로 이벤트가 아닌 **동기 REST**.

## 요청 계약 (sales가 호출할 형태 — `InventoryStockHttpService.outbound`)
```
POST {inventory}/api/v1/stocks/outbound
Authorization: Bearer <JWT>   # 기존 그룹 TokenRelay와 동일
Content-Type: application/json

{
  "referenceNumber": "CO-2026-0001",      // 멱등/원장 참조(= coNumber)
  "lines": [
    { "sku": "OIL-FLT-001", "quantity": 2, "warehouseCode": "WH-BR-001", "unitPrice": 3200 }
  ]
}
```
- **성공**: 200. 각 라인 `outbound`(= `applyOut`)로 지정 창고 차감 + `StockMovement(type=OUT, referenceNumber)`.
- **재고 부족**: 라인 중 하나라도 `availableStock < quantity` → **409** (`INSUFFICIENT_STOCK`). sales가 이를 받아 CO 종료를 차단(CO007)함.
- **멱등(필수·race-safe)**: 같은 `referenceNumber`로 재요청 시 **이중 차감 금지** — 이게 sales 측 이중차감 방어의 **유일한 선**입니다. sales 는 close 에 비관락을 걸지 않습니다(외부효과가 동기 REST 라 '락-중-네트워크IO' 회피 — reserveLine 과 동일 원칙). 따라서 **동시 close 2건/재시도가 와도 단 한 번만 차감**되도록 **레이스세이프**해야 합니다: `movement.referenceNumber` **UNIQUE 제약 + INSERT 충돌 catch**(또는 processed 테이블). check-then-act(비원자)면 TOCTOU 로 이중 차감 발생.
- **올-오어-낫씽(원자)**: 한 라인이라도 부족하면 **어떤 라인도 차감하지 않고 409**(부분 차감 금지). 비관락 안에서 전 라인 가용 선검사 후 일괄 차감.
- **dual-write 잔여**: 차감 성공 후 sales 로컬 커밋 실패 시 orphan 차감 — 위 race-safe dedup 이 있으면 sales 재시도가 안전(이중 차감 없음).

## inventory가 할 일 (작아요)
1. `StockCommandController`에 `@PostMapping("/outbound")` 추가 → 위 바디를 받아 기존 `stockCommandService.outbound(...)` 호출. (~10줄, 기존 reserve/issue 컨트롤러 미러)
2. `INSUFFICIENT_STOCK` → **409** 매핑(이미 ApiException이면 그대로).
3. `referenceNumber` 기반 **레이스세이프 멱등 dedup(필수)** + 라인 **올-오어-낫씽**(위 계약 참조). 동시 close 이중 차감 방어가 여기에 달려 있습니다 — 어려우면 sales 에 알려주세요.

## 참고
- sales 쪽은 구현 완료(이슈 #69, `InventoryPort.shipForCustomerOrder` + rest/stub 어댑터). **rest 모드에서 이 엔드포인트가 live여야 동작**(없으면 CO close가 404/5xx). 그 전까지 sales는 stub(no-op)로 빌드/로컬 동작.
- 트리거=close(인도 완료=물리 출고), 안전재고 floor는 이번 범위 밖(추후).
