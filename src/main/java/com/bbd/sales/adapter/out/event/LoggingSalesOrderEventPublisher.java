package com.bbd.sales.adapter.out.event;

import com.bbd.sales.application.port.out.SalesOrderEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 이벤트 발행 아웃바운드 어댑터(임시 스텁: 로그만).
 * TODO: 트랜잭셔널 아웃박스 + Kafka 발행으로 교체.
 */
@Slf4j
@Component
public class LoggingSalesOrderEventPublisher implements SalesOrderEventPublisher {

    @Override public void publishRequested(String soNumber) { log.info("[event] SalesOrderRequested {}", soNumber); }
    @Override public void publishUpdated(String soNumber)   { log.info("[event] SalesOrderUpdated {}", soNumber); }
    @Override public void publishCanceled(String soNumber)  { log.info("[event] SalesOrderCanceled {}", soNumber); }
    @Override public void publishApproved(String soNumber)  { log.info("[event] SalesOrderApproved {}", soNumber); }
    @Override public void publishRejected(String soNumber)  { log.info("[event] SalesOrderRejected {}", soNumber); }
    @Override public void publishReceived(String soNumber)  { log.info("[event] SalesOrderReceived {}", soNumber); }
}
