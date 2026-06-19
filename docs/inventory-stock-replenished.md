**이벤트 발행 추가 요청**
## (추가 요청) 입고 완료 시 sales로 "재고 보충" 통지 이벤트 발행

### 요청
`procurement.stock-in-requested`를 받아 **실제로 재고를 증가시킨 뒤**, 그 입고가 **SO 연계분(soId/soNumber 있음)이면** sales로 새 통지 이벤트 `inventory.stock-replenished`를 발행해 주세요. sales HQ 담당자가 이 알림을 보고 해당 출고요청의 백오더 라인을 충족(IN_FULFILLMENT) 처리합니다.

### 왜 procurement가 아니라 inventory인가
- `procurement.stock-in-requested`는 **"재고 올려줘"라는 요청**이지 **"올라갔다"는 확정이 아님.** 그걸로 sales를 깨우면, inventory가 실제 증가시키기 전에 sales가 재예약을 시도해 **재고가 아직 없는 경합**이 생깁니다(두 컨슈머 그룹이 같은 토픽을 독립 소비하므로 sales가 앞설 수 있음).
- 통지는 **실제 상태 변화(=inventory가 재고를 올린 시점)** 를 따라야 정확합니다. **inventory는 입고 이벤트에서 soNumber를 이미 받으므로**, 증가 직후 그대로 sales에 echo만 하면 됩니다.

### 이벤트 정의 (신규 토픽)
- 토픽: `inventory.stock-replenished` · key: `soNumber`
- 발행 시점/조건: `onStockIn`에서 `applyIn`(재고 증가) **성공 후**, 소비한 `StockInRequested.soId/soNumber != null`일 때만. (SO 무관 일반 입고는 발행 안 함)
- DTO(복붙용, 공통 envelope 4필드 준수):

```java
public record StockReplenished(
        String eventId,        // UUID — 컨슈머 멱등 키
        String source,         // "inventory" 고정
        String eventType,      // "STOCK_REPLENISHED" 고정
        String occurredAt,     // ISO-8601 UTC Instant
        String soNumber,       // 연관 출고요청 번호 — sales 백오더 트리거 키
        List<Line> lines
) {
    public record Line(
            String sku,
            int quantity,          // 실제 입고된 수량
            String warehouseCode   // 입고 창고
    ) {}
}
```

### sales 측 처리(참고)
- 구독 그룹 `sales-backorder`, `eventId` 멱등 가드 → HQ 알림 생성 → 담당자가 수동으로 백오더 라인 충족.

### 구현 노트
- inventory는 현재 **컨슈머만** 있고 producer 배선이 없습니다(`KafkaTemplate`은 주석 처리된 DLT용으로만 존재). 이 이벤트 발행을 위해 **outbox + relay(또는 최소한 `KafkaTemplate.send`)** 가 필요합니다 — 재고 증가 트랜잭션과 같은 커밋으로 outbox INSERT 권장.
- **soId vs soNumber 표기 통일 필요**: 계약서 `StockInRequested`는 `soId`, procurement 실제 레코드는 `soNumber`. inventory가 받은 값을 그대로 `StockReplenished.soNumber`로 넣어주면 됩니다(이름만 합의).

### 계약 변경
- 기존 계약(`eda-event-spec.md`)은 **sales가 `procurement.stock-in-requested`를 직접 구독**(group `sales-backorder`)하도록 돼 있었으나, 위 경합 때문에 **sales는 그 구독을 빼고 `inventory.stock-replenished`를 구독**하는 것으로 변경. 이벤트 맵(§1)에 신규 토픽 1줄 추가 + §8 "출고/입고 완료 통지 inventory→sales"를 정식 편입.