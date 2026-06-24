package com.bbd.sales.adapter.out.procurement;

import com.bbd.sales.adapter.out.event.OutboxEvent;
import com.bbd.sales.adapter.out.event.OutboxRepository;
import com.bbd.sales.application.port.out.ProcurementPort;
import com.bbd.sales.application.port.out.ShortfallLine;
import com.bbd.sales.global.error.ApiException;
import com.bbd.sales.global.error.dto.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

/**
 * 조달(PO) 아웃바운드 어댑터 — 구매요청(PR)을 Transactional Outbox에 적재해 {@code sales.purchase-requested}로 발행.
 * <p>
 * approve()의 @Transactional 안에서 실행되어 SO 확정과 같은 커밋(원자적, dual-write 회피).
 * 폴러(OutboxPoller)가 outbox 행을 Kafka로 중계(at-least-once → 컨슈머 eventId 멱등 전제).
 * #40: ProcurementStubAdapter(로그 스텁) 제거로 ProcurementPort 단일 구현이 됨 → @Primary 불필요.
 */
@Component
@RequiredArgsConstructor
public class OutboxProcurementAdapter implements ProcurementPort {

    public static final String TOPIC = "sales.purchase-requested";
    private static final String SOURCE = "sales";
    private static final String EVENT_TYPE = "PURCHASE_REQUESTED";

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper; // Boot 자동 구성 빈(FAIL_ON_UNKNOWN_PROPERTIES off)

    @Override
    public void requestPurchase(String soNumber, String destinationWarehouseCode, List<ShortfallLine> lines) {
        // #55 CS-4: eventId 를 결정적 키로(주문당 PR 1건). 호출별 랜덤 UUID 였을 때는 재발행/재시도가
        //           컨슈머에 서로 다른 eventId 로 보여 PO 중복(=돈) 위험. soNumber 앵커로 멱등 보장.
        String eventId = "PR:" + soNumber;
        List<PurchaseRequested.Line> eventLines = lines.stream()
                // sourcingType 은 enum -> 계약 문자열("BUY"/"MAKE"). null 이면 그대로 null(procurement 가 item 마스터로 폴백).
                .map(l -> new PurchaseRequested.Line(
                        l.sku(), l.quantity(),
                        l.sourcingType() == null ? null : l.sourcingType().name()))
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
