# 가격 정책 — 기준단가 · 거래가 스냅샷 · 재고 단가

> 버전 v3 (2026-06-10) · 작성: sales팀 · 대상: sales / procurement / item / inventory
> 이벤트 연동 스펙은 [BBD-AES EDA 이벤트 계약](./eda-event-spec.md)(생산자별 토픽 · 이벤트별 DTO · plain spring-kafka) 참고.
> v2 → v3: 스펙 문서가 eda-event-spec.md로 통합되어 링크만 갱신 — 가격 규칙 자체는 변동 없음.

## 0. 결론 한 줄

**ERP의 가격은 한 종류가 아니다. 세 종류를 구분하고, 서로 덮어쓰지 않는다.**

| # | 가격 | 위치 | 성격 | 갱신 |
|---|---|---|---|---|
| 1 | **현재 기준단가** | `item.unitPrice` | 진실원천(source of truth) | item 서비스에서 덮어쓰기 |
| 2 | **거래가 스냅샷** | SO/PO **라인** | 문서 확정 시점의 계약가 | **불변** (박제) |
| 3 | **재고 단가** | `stock.unitPrice` (신설 확정) | 기준단가의 복제본 | **`ITEM_PRICE_CHANGED` 이벤트로만** |

## 1. 왜 SO/PO는 가격을 스냅샷으로 저장하는가

SO(출고요청)/PO(발주)는 단순 데이터가 아니라 **상사문서(거래 증빙)**다.

1. **과거 문서 금액은 불변이어야 한다.** 라인 단가를 item 마스터 참조로 풀어두면, 마스터 가격이 바뀌는 순간 작년 주문서 합계가 바뀐다. 매출/매입 대사, 세금계산서, 감사 추적이 전부 깨진다.
2. **문서 단가 ≠ 마스터 단가.** PO 단가는 vendor 협상가, SO 단가는 할인이 붙을 수 있는 값이다. 마스터 참조로는 애초에 표현이 안 된다.
3. **3-way match.** 발주–입고–송장 대사는 "주문 시점에 합의한 가격" 기준으로 한다.

### 현재 코드 현황 — 이미 전부 이 원칙대로 구현돼 있음 (추가 작업 없음, 정책 확정만)

| 서비스 | 구현 | 비고 |
|---|---|---|
| sales | `SalesOrderLine.unitPriceSnapshot` (BigDecimal) | 주석: "주문 시점의 상품명·단가를 박제" |
| procurement | `PurchaseOrderLine.unitPrice` + `subtotal` (BigDecimal) | 발주 시점 스냅샷 |
| item | `Item.unitPrice` (int) 덮어쓰기 | 주석: "변경 이력은 각 Procurement & Sales & Inventory가 저장" |

## 2. `stock.unitPrice` 운용 규칙 (inventory — 컬럼 신설 확정)

`stock.unitPrice`는 **"현재 기준단가의 로컬 복제본"**이다. 조회/재고 평가 때 item 서비스를 호출하지 않기 위해 둔다. (inventory가 이미 `name`/`category`/`safetyStock`을 비정규화 복제하는 것과 같은 패턴.)

### 갱신 규칙

| 이벤트 (토픽) | `stock.unitPrice` | 거래가(unitPrice 필드) 처리 |
|---|---|---|
| `ItemPriceChanged` (`item.price-changed`) | ✅ **갱신** | — |
| `StockInRequested` (`procurement.stock-in-requested`) | ❌ 건드리지 않음 | 재고 이동 이력(movement)에 기록 |
| `StockOutRequested` (`sales.stock-out-requested`) | ❌ 건드리지 않음 | 재고 이동 이력(movement)에 기록 |

**입출고 이벤트의 거래가로 `stock.unitPrice`를 덮어쓰면 안 되는 이유:**
거래가는 그 건의 협상가/할인가다. 그걸 기준단가 자리에 쓰면 vendor 한 곳의 협상가가 전사 기준단가를 오염시킨다. 거래가의 자리는 movement 이력이고, 기준단가의 자리는 item → `ITEM_PRICE_CHANGED` 경유 복제다.

### 초기값

- 신규 stock 생성 시: item 서비스 조회(기존 `RestItemClient` 경로)로 현재 `unitPrice`를 받아 세팅.
- 이후에는 이벤트로만 갱신 — 같은 sku의 가격변동은 key=sku 파티셔닝으로 순서가 보장된다.

## 3. 시나리오로 보는 전체 흐름

```
1) item: SKU-1001 단가 150,000 → 155,000 변경
   └─ ITEM_PRICE_CHANGED 발행 → inventory: stock.unitPrice = 155,000

2) procurement: SKU-1001 100개를 vendor 협상가 120,000에 발주 → 입고
   └─ PO 라인 unitPrice = 120,000 (스냅샷, 불변)
   └─ STOCK_IN_REQUESTED 발행 → inventory: 수량 +100, movement에 단가 120,000 기록
      (stock.unitPrice는 여전히 155,000 — 건드리지 않음)

3) sales: SKU-1001 3개 출고요청 작성 시점 단가 155,000
   └─ SO 라인 unitPriceSnapshot = 155,000 (스냅샷, 불변)
   └─ 이후 item이 160,000으로 올려도 이 주문 금액은 465,000 그대로
   └─ HQ 승인 시 STOCK_OUT_REQUESTED 발행 → inventory: 수량 -3, movement에 155,000 기록
```

## 4. 자주 나올 질문

- **Q. 가격 변경 이력 테이블을 item에 둬야 하지 않나?**
  현 설계는 "이력은 소비 서비스가 각자 저장"(item 코드 주석)이다. SO/PO 라인 스냅샷 + inventory movement가 곧 이력이다. item에 별도 이력 테이블은 필요해지면 그때 추가(스펙 영향 없음).
- **Q. 단가 타입이 서비스마다 다른데?**
  진실원천 item이 `int`(원화 정수)이므로 **와이어(이벤트)는 원화 정수로 통일**. BigDecimal 쪽은 `intValueExact()` 변환. 원화는 소수점이 없어 안전하다.
- **Q. stock.unitPrice로 재고자산 평가까지 하나?**
  현 단계에서는 "현재가 기준 평가/조회용"이다. 원가법(이동평균/총평균) 평가가 필요해지면 movement의 거래가 이력으로 계산하면 된다 — 그래서 거래가를 movement에 남기는 것이 중요하다.
