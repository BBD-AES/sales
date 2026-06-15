package com.bbd.sales.adapter.out.procurement;

import com.bbd.sales.adapter.out.event.OutboxEvent;
import com.bbd.sales.adapter.out.event.OutboxRepository;
import com.bbd.sales.application.port.out.ProcurementPort;
import com.bbd.sales.application.port.out.StockTransferLine;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 조달(PO) 아웃바운드 어댑터 — 구매요청(PR)을 Transactional Outbox에 적재해 {@code sales.purchase-requested}로 발행.
 * <p>
 * approve()의 @Transactional 안에서 실행되어 SO 확정과 같은 커밋(원자적, dual-write 회피).
 * 폴러(OutboxPoller)가 outbox 행을 Kafka로 중계(at-least-once → 컨슈머 eventId 멱등 전제).
 * {@code @Primary}로 ProcurementStubAdapter(로그 스텁) 대신 주입된다(스텁은 폴백으로 잔존).
 */
@Primary
@Component
@RequiredArgsConstructor
public class OutboxProcurementAdapter implements ProcurementPort {

    public static final String TOPIC = "sales.purchase-requested";
    private static final String SOURCE = "sales";
    private static final String EVENT_TYPE = "PURCHASE_REQUESTED";

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper; // Boot 자동 구성 빈(FAIL_ON_UNKNOWN_PROPERTIES off)

    @Override
    public void requestPurchase(String soNumber, String destinationWarehouseCode, List<StockTransferLine> lines) {
        String eventId = UUID.randomUUID().toString();
        List<PurchaseRequested.Line> eventLines = lines.stream()
                .map(l -> new PurchaseRequested.Line(l.sku(), l.quantity()))
                .toList();
        var event = new PurchaseRequested(
                eventId, SOURCE, EVENT_TYPE, Instant.now().toString(),
                soNumber, destinationWarehouseCode, eventLines);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            // 직렬화 실패 → unchecked 전파로 approve() 트랜잭션 롤백. 에러코드만 명확히 감싼다.
            throw new ApiException(ErrorCode.EVENT_SERIALIZATION_FAILED);
        }
        // key=soNumber(같은 주문 같은 파티션 → 순서 보장). 같은 트랜잭션에서 outbox INSERT.
        outbox.save(new OutboxEvent("SalesOrder", soNumber, EVENT_TYPE, payload, eventId, TOPIC));
    }
}
