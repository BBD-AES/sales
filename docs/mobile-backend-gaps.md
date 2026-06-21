# 모바일 화면 연동 중 발견한 백엔드 갭

> 작성 2026-06-21 · 모바일(BBD-AES/mobile) 화면을 실 API로 연동하며 발견. 인증과 무관(토큰 없이도 진행 중인 화면 연동에서 드러난 계약 갭).

## 🔴 Gap 1 — inventory: 재고 **목록** 응답이 얇아 모바일 재고 화면 연동 불가

- **엔드포인트:** `GET /inventory/api/v1/stocks` → `PageResponse<StockListItemResponse>`
- **현재 목록 DTO(`StockListItemResponse`):** `sku · name · currentStock · warehouseCode` 4개뿐.
- **모바일 재고 카드가 필요로 하는 것:** `safetyStock`(상태 부족/없음/정상 판정), `availableStock`, `category`(카테고리 필터 칩), `unit`(단위 표기).
- **사실:** `Stock` 엔티티엔 이 필드가 다 있고, **단건** `GET /stocks/{warehouseCode}/{sku}`(`StockResponse`)는 `category·unit·availableStock·safetyStock·unitPrice·location`까지 전부 노출. **목록 DTO만 얇음.**
- **요청:** `StockListItemResponse`에 **`safetyStock · availableStock · category · unit`** 추가(가능하면 `unitPrice·location`도). 그러면 모바일 재고 화면을 N+1 없이 즉시 연동 가능.
- **현재 검색 필터:** `StockSearchCondition`(warehouseCode·sku·category·belowSafety) — 모바일 부족 필터=`belowSafety=true`, 카테고리 필터=`category`로 매핑 가능. 상태(없음=0) 필터는 클라이언트 계산.

> 이 보강 전엔 모바일 재고 화면은 보류(목록만으론 상태 배지·단위·카테고리 못 그림). 보강되면 바로 PR 올리겠습니다.

## 🟡 Gap 2 — sales: SO **목록 요약**에 라인 정보 없음 (화면 풍부도)

- **엔드포인트:** `GET /sales/api/v1/sales-orders` → `SalesOrderPageResponse<SalesOrderSummaryResponse>`
- **현재 요약 DTO:** soNumber·warehouse·status·actors·timestamps·totalAmount·note. **라인(SKU·수량) 없음.**
- **영향:** 모바일 발주/작업이력 카드를 **주문 단위**(SO번호·상태·합계·날짜)로만 렌더 중. "N품목 M개" 같은 표기 불가.
- **선택지:**
  - (A) 요약에 **`itemCount`·`totalQuantity`** 추가 → 카드가 "N품목 총 M개"까지 표시(가벼움, 권장).
  - (B) 모바일이 상세 `GET /{soNumber}`(라인 포함)를 행마다 호출 → **N+1**, 비권장.
- 현재는 (선택 전) 주문 단위로 정직하게 렌더하고 추측 안 함.

## 🟠 Gap 3 — 모바일 입고(receive) 흐름이 백엔드 의미와 불일치 (모바일/설계)

- **백엔드:** 입고 = `PATCH /sales/api/v1/sales-orders/{soNumber}/receive` — **주문 단위**(도착한 발주 1건을 IN_FULFILLMENT→RECEIVED 로 전환).
- **현재 Compose Scan 화면:** **부품 단위** — 부품 바코드 스캔 → 수량 입력 → "입고 등록"(그 부품 재고 +N). soNumber 개념 없음, 의미도 다름(부품 가산 vs 발주 상태 전환).
- **결과:** Scan의 "입고 등록"을 `repo.receive(soNumber)`에 그대로 배선 불가. **데이터 레이어(repo.arrivals/receive)는 준비됨.**
- **필요:** 원래 설계대로 **도착 대기 큐**(= `GET ?status=IN_FULFILLMENT&to_warehouse_code=`) → 발주 탭 → 확인 → `receive` UI. (디자인 프로토타입엔 bell→ArrivalQueue→입고확인 으로 있었으나 이 Compose 포팅이 부품 스캔으로 대체함.) 화면/UX 결정 사항 → 사용자/디자인 합의 후 진행(임의 신규 화면 추가는 보류).

## 진행 상태(모바일 화면 연동)
- ✅ 보충 발주(Order) — 연동(PR #5 머지됨)
- ✅ 작업 이력(Worklog) — 연동(PR #7)
- ✅ 재고 조회(Inventory) + 게이트웨이 멀티서비스 — 연동(PR #9, Gap 1 보강 전제)
- ⏸️ 입고 확정(receive) — 데이터 레이어 준비됨, **UI는 Gap 3(도착 큐 흐름) 결정 대기**
- 🔒 마이(/me) — user 서비스 + 인증 토큰 필요
