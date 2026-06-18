package com.bbd.sales.adapter.out.warehouse;

import com.bbd.sales.adapter.out.warehouse.dto.WarehouseResponse;
import com.bbd.sales.application.port.out.WarehousePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * [역할]: 포트(WarehousePort)의 '실제 REST 구현'. 헥사고날의 어댑터 자리.
 * 서비스는 WarehousePort만 알고, 이게 inventory를 실제로 호출하는 일을 담당.
 * 창고명은 '표시용 스냅샷'(비핵심)이라 실패해도 주문을 막지 않고 코드로 폴백한다.
 */
@Primary // WarehouseStubAdapter보다 우선해서 주입됨
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseRestAdapter implements WarehousePort {

    private final InventoryHttpService client;

    @Override
    public String warehouseName(String warehouseCode) {
        if (warehouseCode == null) return null;
        try {
            // 실제 GET 호출(역직렬화 까지)
            WarehouseResponse w = client.getByCode(warehouseCode);
            // 응답/이름 있으면 그대로, 없으면 코드로.
            return (w != null && w.name() != null) ? w.name() : warehouseCode;
        } catch (RestClientException e) {
            // inventory 호출 실패(연결거부/타임아웃/4xx·5xx)만 폴백. 어댑터 내부 버그(NPE 등)는 전파시켜 가시화.
            // 창고명은 비핵심 표시값이라 주문 흐름은 죽이지 않고 코드로 폴백. throwable 째로 로깅(원인 추적).
            log.warn("[WarehouseRest] 창고명 조회 실패 code={} -> 코드 폴백", warehouseCode, e);
            return warehouseCode;
        }
    }
}
