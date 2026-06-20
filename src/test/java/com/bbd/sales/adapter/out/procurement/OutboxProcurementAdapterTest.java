package com.bbd.sales.adapter.out.procurement;

import com.bbd.sales.adapter.out.event.OutboxEvent;
import com.bbd.sales.adapter.out.event.OutboxRepository;
import com.bbd.sales.application.port.out.StockTransferLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * OutboxProcurementAdapter 단위테스트.
 * requestPurchase() 가 sales.purchase-requested 계약 이벤트를 outbox에 올바르게 적재하는지 검증한다.
 * 실제 브로커 없이 OutboxRepository만 목으로 두고, 저장된 행(토픽/키/페이로드)을 캡처해 단언한다.
 * 페이로드는 procurement가 복붙해 쓸 record(PurchaseRequested)로 역직렬화해 라운드트립까지 확인.
 */
class OutboxProcurementAdapterTest {

    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final OutboxProcurementAdapter adapter = new OutboxProcurementAdapter(outbox, objectMapper);

    @Test
    @DisplayName("구매요청 → sales.purchase-requested 이벤트를 outbox에 적재(envelope + lines + key)")
    void requestPurchase_enqueuesContractEvent() throws Exception {
        adapter.requestPurchase(
                "SO-2026-000042", "WH-BR-1001",
                List.of(new StockTransferLine("SKU-1001", 3),
                        new StockTransferLine("SKU-2002", 5)));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        // 라우팅: 토픽 = 계약 토픽, 파티션 키 = soNumber
        assertThat(saved.getTopic()).isEqualTo("sales.purchase-requested");
        assertThat(saved.getAggregateId()).isEqualTo("SO-2026-000042");

        // 페이로드 라운드트립 (procurement가 받게 될 record 그대로)
        PurchaseRequested ev = objectMapper.readValue(saved.getPayload(), PurchaseRequested.class);
        assertThat(ev.source()).isEqualTo("sales");
        assertThat(ev.eventType()).isEqualTo("PURCHASE_REQUESTED");
        assertThat(ev.eventId()).isEqualTo("PR:SO-2026-000042"); // 랜덤 UUID 아님(결정적 멱등 키)
        assertThat(ev.occurredAt()).endsWith("Z");        // UTC Instant 직렬화
        assertThat(ev.soNumber()).isEqualTo("SO-2026-000042");
        assertThat(ev.warehouseCode()).isEqualTo("WH-BR-1001");
        assertThat(ev.lines()).hasSize(2);
        assertThat(ev.lines().get(0).sku()).isEqualTo("SKU-1001");
        assertThat(ev.lines().get(0).quantity()).isEqualTo(3);
        assertThat(ev.lines().get(1).sku()).isEqualTo("SKU-2002");
        assertThat(ev.lines().get(1).quantity()).isEqualTo(5);
    }
}
