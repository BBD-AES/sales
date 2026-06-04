package com.bbd.sales.adapter.out.event;

import com.bbd.sales.application.port.out.SalesOrderEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 이벤트 발행 아웃바운드 어댑터(임시 스텁: 로그만).
 * TODO: in-process 도메인 이벤트(@TransactionalEventListener AFTER_COMMIT)로 교체 후, 필요 시 브로커.
 */
@Slf4j
@Component
public class LoggingSalesOrderEventPublisher implements SalesOrderEventPublisher {

    @Override public void publishRequested(String soNumber) { log.info("[event] SalesOrderRequested {}", soNumber); }
    @Override public void publishUpdated(String soNumber)   { log.info("[event] SalesOrderUpdated {}", soNumber); }
    @Override public void publishSubmitted(String soNumber) { log.info("[event] SalesOrderSubmitted {}", soNumber); }
    @Override public void publishCanceled(String soNumber)  { log.info("[event] SalesOrderCanceled {}", soNumber); }
    @Override public void publishFulfilling(String soNumber){ log.info("[event] SalesOrderFulfilling {}", soNumber); }
    @Override public void publishBackordered(String soNumber){ log.info("[event] SalesOrderBackordered {}", soNumber); }
    @Override public void publishRejected(String soNumber)  { log.info("[event] SalesOrderRejected {}", soNumber); }
    @Override public void publishReceived(String soNumber)  { log.info("[event] SalesOrderReceived {}", soNumber); }
}
