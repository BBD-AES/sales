# BBD-AES EDA 이벤트 계약 — item · sales · procurement · inventory

> 버전 v4 (2026-06-15) · 작성: sales팀 · **이 문서가 조직의 이벤트 계약서다. 스펙 변경은 이 문서 PR로 합의한다.**
> 가격 처리 원칙은 [가격 정책 문서](./price-policy.md) 참고.
>
> 변경 이력: v1 단일토픽+단일DTO → v2 생산자별 토픽+이벤트별 DTO(inventory 연동만)
> → v3 조직 전체 통합 + sales의 백오더 구독(`StockInRequested.soId`) 추가
> → **v4 sales→procurement 구매요청 계약 이벤트(`sales.purchase-requested`) 추가 — 기존 동기 PR 스텁을 이벤트로 대체**

## 0. 아키텍처 한 장 요약

```
item ────────── item.price-changed ──────────────────────► inventory (기준단가 복제 갱신)

sales ───────── sales.stock-out-requested ───────────────► inventory (재고 차감)
sales ───────── sales.purchase-requested ────────────────► procurement (부족분 BUY 구매요청 PR)

procurement ─── procurement.stock-in-requested ──┬───────► inventory (재고 증가)
                                                 └───────► sales     (백오더 충족 트리거)

(sales ── sales.order.* ── sales 자체 알림용 내부 채널 — 본 계약 범위 밖)
```

| 원칙 | 결정 |
|---|---|
| 토픽 | **이벤트당 1개**, 이름 = `<생산자>.<이벤트-kebab>` |
| DTO | **이벤트당 record 1개** — null 늪 없음, 타입이 곧 검증 |
| 라이브러리 | **plain spring-kafka, 4팀 통일 (SCS 미사용)** — `spring-boot-starter-kafka` |
| 직렬화 | **JSON 문자열** (key/value String serializer, 타입 헤더 없음 → Jackson 버전·스택 무관) |
| 발행 | 각 서비스 **Transactional Outbox** → 폴러가 Kafka 발행 (도메인 저장과 outbox INSERT는 한 트랜잭션) |
| 전달 보장 | at-least-once → **컨슈머 `eventId` 멱등 필수** |
| 진화 | 기존 DTO는 **nullable 필드 추가만**, 새 이벤트는 **새 토픽+새 record** |
| 브로커 | `kafka.inwoohub.com:9092` (PLAINTEXT) · UI: https://kafka-ui.inwoohub.com/ |

DTO record는 발행 레포와 구독 레포에 **동일하게 복붙**한다 (JSON 운반이라 공유 jar 불필요).

## 1. 이벤트 맵 (계약 전체 목록)

| 토픽 | 생산자 | 구독자 (컨슈머 그룹) | DTO | 메시지 key | 의미 |
|---|---|---|---|---|---|
| `sales.stock-out-requested` | sales | inventory (`stock-group`) | `StockOutRequested` | soNumber | 출고 확정(HQ 승인) → 재고 차감 요청 |
| `sales.purchase-requested` | sales | procurement (`procurement-purchase`) | `PurchaseRequested` | soNumber | HQ 승인 시 부족분(BUY) 발생 → 구매요청(PR) |
| `procurement.stock-in-requested` | procurement | inventory (`stock-group`), sales (`sales-backorder`) | `StockInRequested` | poNumber | PO 입고 확정 → 재고 증가 / SO 백오더 충족 트리거 |
| `item.price-changed` | item | inventory (`stock-group`) | `ItemPriceChanged` | sku | 기준단가 변경 → 재고 단가 복제본 갱신 |

- **하나의 토픽을 여러 서비스가 구독해도 된다** — 컨슈머 그룹이 다르면 각자 독립적으로 전량 수신한다 (`procurement.stock-in-requested`가 그 예).
- 컨슈머 그룹 명명: `<서비스>-<용도>` (inventory의 `stock-group`은 기존 합의 유지).
- `item.price-changed`는 key=sku이므로 추후 `cleanup.policy=compact`(품목별 최신가만 보존) 적용 후보.
- sales의 기존 알림 토픽(`sales.order.*`, 그룹 `sales-hq-notification`)은 sales 내부 채널 — **타 서비스는 구독하지 않는다**(비계약, 예고 없이 바뀔 수 있음). procurement가 필요로 하던 "구매요청 트리거"는 이 내부 토픽이 아니라 위 `sales.purchase-requested` 계약 이벤트로 받는다.
- **백오더 한 바퀴**: `sales.purchase-requested` → (procurement가 PO 작성/입고) → `procurement.stock-in-requested`(`soId`=원 `soNumber`) → sales 백오더 충족. procurement는 `PurchaseRequested.soNumber`를 PO에 보관했다가 입고 이벤트의 `soId`로 되돌려준다.

## 2. 공통 규칙

### envelope — 모든 이벤트 DTO가 동일하게 갖는 메타 4필드

| 필드 | 타입 | 규칙 |
|---|---|---|
| `eventId` | String(UUID) | 이벤트 1건당 유일. 컨슈머 멱등성 키 |
| `source` | String | 발행 서비스. `sales` / `procurement` / `item` / `inventory` (소문자 고정) |
| `eventType` | String | UPPER_SNAKE 고정, record 이름과 1:1 |
| `occurredAt` | String | ISO-8601 **UTC Instant** (`Instant.toString()`, 예: `2026-06-10T03:12:45Z`). LocalDateTime 금지 |

토픽이 이미 출처를 말해주지만 DLQ·통합 로그에서 **메시지만 보고도 식별**되도록 payload에도 남긴다.

### 값 규칙

- **단가는 원화 정수(int)** — 진실원천 `item.unitPrice`가 int. BigDecimal 보유 서비스는 `intValueExact()` 변환 (원화라 안전).
- `warehouseCode`는 inventory `Warehouse.code` 형식 (예: `WH-HQ-001`).
- 문서번호 형식: soNumber=`SO-...`, poNumber=`PO-YYYY-NNNNNN`.

## 3. DTO 정의 (복붙용) + JSON 예시

### 3-1. `StockOutRequested` — sales 발행, HQ 승인(IN_FULFILLMENT 전이) 시점

```java
public record StockOutRequested(
        String eventId,
        String source,       // "sales" 고정
        String eventType,    // "STOCK_OUT_REQUESTED" 고정
        String occurredAt,
        String soNumber,
        List<Line> lines
) {
    public record Line(
            String sku,
            int quantity,
            String warehouseCode,   // 라인별 출발(출고) 창고
            int unitPrice           // 거래가 스냅샷(원) — movement 이력 기록용
    ) {}
}
```

```json
{ "eventId": "7f3a9c2e-...", "source": "sales", "eventType": "STOCK_OUT_REQUESTED",
  "occurredAt": "2026-06-10T03:12:45Z", "soNumber": "SO-2026-000042",
  "lines": [ { "sku": "SKU-1001", "quantity": 3, "warehouseCode": "WH-HQ-001", "unitPrice": 150000 } ] }
```

### 3-2. `StockInRequested` — procurement 발행, PO RECEIVED 전이 시점

```java
public record StockInRequested(
        String eventId,
        String source,       // "procurement" 고정
        String eventType,    // "STOCK_IN_REQUESTED" 고정
        String occurredAt,
        String poNumber,
        String soId,         // ★ nullable. SO 연계 발주(PO.soId 보유)일 때만 채움 — sales 백오더 트리거용
        List<Line> lines
) {
    public record Line(
            String sku,
            int quantity,
            String warehouseCode,   // 입고 창고 (PO 헤더의 warehouseCode)
            int unitPrice           // 거래가(협상가) 스냅샷(원) — movement 이력 기록용
    ) {}
}
```

```json
{ "eventId": "a91b44d0-...", "source": "procurement", "eventType": "STOCK_IN_REQUESTED",
  "occurredAt": "2026-06-10T05:01:10Z", "poNumber": "PO-2026-000007", "soId": "SO-2026-000042",
  "lines": [ { "sku": "SKU-1001", "quantity": 100, "warehouseCode": "WH-HQ-001", "unitPrice": 120000 } ] }
```

### 3-3. `ItemPriceChanged` — item 발행, `updatePrice()`/`update()` 시점

```java
public record ItemPriceChanged(
        String eventId,
        String source,       // "item" 고정
        String eventType,    // "ITEM_PRICE_CHANGED" 고정
        String occurredAt,
        String sku,
        int unitPrice        // 새 기준단가(원)
) {}
```

```json
{ "eventId": "c44d1f08-...", "source": "item", "eventType": "ITEM_PRICE_CHANGED",
  "occurredAt": "2026-06-10T06:30:00Z", "sku": "SKU-1001", "unitPrice": 155000 }
```

### 3-4. `PurchaseRequested` — sales 발행, HQ 승인 시 부족분 BUY 라우팅 시점

```java
public record PurchaseRequested(
        String eventId,
        String source,        // "sales" 고정
        String eventType,     // "PURCHASE_REQUESTED" 고정
        String occurredAt,
        String soNumber,      // 연관 수주(= 메시지 key). procurement는 PO.soId로 보관 → 입고 시 백오더 트리거로 회신
        String warehouseCode, // 입고 목적지 창고(수주 도착창고)
        List<Line> lines
) {
    public record Line(
            String sku,
            int quantity      // 부족 수량(구매 요청 수량). 단가 없음 — 협상가는 procurement가 PO에서 결정
    ) {}
}
```

```json
{ "eventId": "5d8e1a3b-...", "source": "sales", "eventType": "PURCHASE_REQUESTED",
  "occurredAt": "2026-06-15T02:20:00Z", "soNumber": "SO-2026-000042", "warehouseCode": "WH-BR-1001",
  "lines": [ { "sku": "SKU-1001", "quantity": 7 } ] }
```

> 발행 시점: sales가 HQ 승인(`approve`)에서 가용분 예약 후 **남은 부족분 중 sourcingType=BUY**인 라인만 모아 1건 발행. SO 확정과 같은 트랜잭션의 outbox INSERT(원자적). 생산(MAKE) 라우팅은 별개 채널(본 계약 밖).

## 4. 컨슈머 구현 규칙

### 공통 패턴 — 멱등 가드 + 처리 + 기록을 한 트랜잭션으로

```java
@KafkaListener(topics = "sales.stock-out-requested", groupId = "stock-group")
@Transactional
public void onStockOut(String message) {
    StockOutRequested e = objectMapper.readValue(message, StockOutRequested.class);
    if (processedEventRepository.existsByEventId(e.eventId())) return;   // 멱등 (at-least-once 대비)
    stockCommandService.outbound(e);
    processedEventRepository.save(new ProcessedEvent(e.eventId()));
}
```

### inventory — 리스너 3개 (분기 코드 없음, 토픽이 라우팅)

| 토픽 | 처리 |
|---|---|
| `sales.stock-out-requested` | `currentStock/availableStock -= qty` + movement(OUT, 거래가) 기록 |
| `procurement.stock-in-requested` | stock 없으면 생성(카탈로그 조회로 초기 단가 포함) → `+= qty` + movement(IN, 거래가) 기록 |
| `item.price-changed` | `UPDATE stock SET unit_price=? WHERE sku=?` — **창고별 행 전부 벌크 갱신**. 수량 불변 |

- **`stock.unitPrice`는 `ItemPriceChanged`로만 갱신.** 입출고의 `unitPrice`(거래가)로 덮어쓰기 금지 → [가격 정책](./price-policy.md).
- 같은 sku의 "가격변동 ↔ 입출고"는 **토픽이 달라 순서 비보장** — 가격 이벤트는 단가만, 이동 이벤트는 수량+이력만 건드리는 분리 규율을 지킬 것(이동 처리가 최신 단가에 의존하는 로직 금지).
- 출고 시 재고 부족: 예외 → 재시도 → DLT 적재 + reconcile에서 보정 (동기 응답이 불가능한 채널임을 전제).

### sales — 백오더 충족 리스너 (그룹 `sales-backorder`)

```java
@KafkaListener(topics = "procurement.stock-in-requested", groupId = "sales-backorder")
@Transactional
public void onStockIn(String message) {
    StockInRequested e = objectMapper.readValue(message, StockInRequested.class);
    if (e.soId() == null) return;                                        // SO 연계 발주만 관심
    if (processedEventRepository.existsByEventId(e.eventId())) return;
    backorderService.onPurchaseArrived(e.soId(), e);                     // BACKORDERED 검증 → fulfillBackorder 흐름
    processedEventRepository.save(new ProcessedEvent(e.eventId()));
}
```

### 에러 처리 (전 서비스 공통) — 빈 하나로 토픽별 DLT 자동

```java
@Bean
DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
    return new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(template),   // 실패 메시지 → <원본토픽>.DLT
            new FixedBackOff(1000L, 2));                   // 1초 간격 2회 재시도
}
```

## 5. 스키마 진화 규칙

1. **기존 DTO의 필드는 추가만** (항상 nullable). 삭제·이름변경·타입변경 금지.
2. **모르는 필드는 무시** — Spring 주입 ObjectMapper는 기본이 무시. 직접 `new ObjectMapper()` 시 `FAIL_ON_UNKNOWN_PROPERTIES` 비활성 확인.
3. **새 이벤트 = 새 토픽 + 새 record** — 기존 토픽·DTO·리스너 영향 0.
4. **새 구독자 = 새 컨슈머 그룹** — 발행자 변경 없이 붙는다 (`sales-backorder`가 선례).
5. 위 전부 이 문서 PR로 합의 후 반영.

## 6. 서비스별 구현 체크리스트

공통: outbox 행에 **topic 컬럼(또는 eventType→topic 매핑)** — 토픽이 여러 개이므로 폴러가 행만 보고 발행처를 알아야 한다.

### sales (발행 2 + 구독 1)
- [x] Outbox + 폴러 가동 중 — 재사용. **outbox에 `topic` 컬럼 추가 완료**(폴러가 행의 topic으로 발행, 레거시 null은 `sales.order.*` 규칙으로 폴백)
- [x] `PurchaseRequested` 발행: HQ 승인(`approve`)의 부족분 BUY 라우팅 시점, topic=`sales.purchase-requested`, key=soNumber. 기존 동기 PR 스텁(`ProcurementStubAdapter`)을 `OutboxProcurementAdapter`(@Primary)로 대체
- [ ] `StockOutRequested` 발행: HQ 승인(IN_FULFILLMENT 전이) 시점, key=soNumber (예정)
- [ ] `procurement.stock-in-requested` 구독 (그룹 `sales-backorder`) + `processed_event` 테이블 + 백오더 충족 유스케이스

### procurement (발행 1)
- [x] outbox_event 테이블 + MessageRelay(1초 폴링) 완비
- [ ] `spring-boot-starter-kafka` + 브로커 설정, `MessageRelay.publish()` 로그 스텁 → `KafkaTemplate.send(topic, key, payload)`
- [ ] 입고 유스케이스/엔드포인트 신설(`markReceived()`는 도메인에만 있음) → 같은 트랜잭션에서 `StockInRequested`(soId 포함) outbox INSERT
- ⚠️ 기존 `DomainEvent` 인터페이스 경유 금지 — DTO를 직접 직렬화해 payload 저장 (`occurredAt` LocalDateTime 충돌)

### item (발행 1)
- [ ] EDA 신규 도입: procurement의 outbox 구조(테이블+relay) 복사 권장
- [ ] `spring-boot-starter-kafka` + 브로커 설정
- [ ] `updatePrice()` / `update()` 두 곳에서 저장과 같은 트랜잭션으로 `ItemPriceChanged` outbox INSERT, key=sku

### inventory (구독 3)
- [ ] `spring-boot-starter-kafka` + 컨슈머 설정 (그룹 `stock-group`, String deserializer, `auto-offset-reset: earliest`)
- [ ] `Stock.unitPrice` 컬럼 추가 — 기존 행 있는 테이블이라 `Integer`(또는 default 0)로. 신규 stock 생성 시 item 동기 조회로 초기값(기존 `RestItemClient`의 응답에 unitPrice 필드 추가)
- [ ] `ProcessedEvent`(event_id unique) + `StockMovement`(sku, warehouseCode, type IN/OUT, quantity, unitPrice, referenceNumber, occurredAt) 테이블
- [ ] 리스너 3개(§4) + 커맨드: `outbound` / `inbound` / `updateUnitPrice`
- [ ] reconcile 배치(일 1회): `item.unitPrice` vs `stock.unitPrice` 대사 + (선택) movement 합계 vs `currentStock` 검증

## 7. 공통 함정 (전 서비스 Boot 4.0.6 / Java 21)

- **의존성은 반드시 `org.springframework.boot:spring-boot-starter-kafka`.** raw `org.springframework.kafka:spring-kafka`만 넣으면 Boot 4.0 모듈 분리로 `KafkaTemplate` 빈이 안 생겨 **기동 실패** (sales에서 실증).
- Boot 4.0은 Jackson 2/3 혼재 — **JSON 문자열만** 운반하는 본 계약이면 서비스 간 Jackson 버전 차이 무관.
- `occurredAt`은 `Instant` 기준 — 오프셋 없는 LocalDateTime 직렬화 금지.
- SCS(Spring Cloud Stream)는 **사용하지 않는다** (4팀 합의: 리스너 수 소규모 + 스트림 처리 없음 → plain 통일). 재검토 트리거: 한 서비스의 구독 토픽 6~8개 초과 또는 Kafka Streams급 실시간 집계 요구 발생 시.

## 8. 확장 예약 (비계약 — 필요해지면 이 문서 PR로 정식 편입)

| 후보 | 방향 | 비고 |
|---|---|---|
| 재고부족 → 구매요청 | inventory → procurement | inventory EDA.md의 `inventory.purchase-requested` 구상. 현재는 PO를 사람이 생성하므로 미편입 |
| 출고 완료 통지 | inventory → sales | 차감 성공/실패 결과 회신이 필요해지면 |
| sales 알림 외부화 | sales → 타 서비스 | `sales.order.*`를 계약으로 승격할 일이 생기면 |
