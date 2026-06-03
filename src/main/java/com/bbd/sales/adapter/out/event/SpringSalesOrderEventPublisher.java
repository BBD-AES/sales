package com.bbd.sales.adapter.out.event;

import com.bbd.sales.application.port.out.SalesOrderEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 이벤트 발행 아웃바운드 어댑터: in-process(Spring ApplicationEvent).
 * 발행은 서비스 트랜잭션 안에서 일어나고, 실제 처리는 AFTER_COMMIT 리스너가 맡는다
 * (커밋된 사실만 부수효과로 흘림 = in-process outbox-lite). 추후 브로커로 교체해도 포트/서비스 불변.
 */
@Component
@RequiredArgsConstructor
public class SpringSalesOrderEventPublisher implements SalesOrderEventPublisher {

    private final ApplicationEventPublisher publisher;

    @Override public void publishRequested(String soNumber)  { publisher.publishEvent(new SalesOrderEvent.Requested(soNumber)); }
    @Override public void publishUpdated(String soNumber)    { publisher.publishEvent(new SalesOrderEvent.Updated(soNumber)); }
    @Override public void publishSubmitted(String soNumber)  { publisher.publishEvent(new SalesOrderEvent.Submitted(soNumber)); }
    @Override public void publishCanceled(String soNumber)   { publisher.publishEvent(new SalesOrderEvent.Canceled(soNumber)); }
    @Override public void publishFulfilling(String soNumber) { publisher.publishEvent(new SalesOrderEvent.Fulfilling(soNumber)); }
    @Override public void publishBackordered(String soNumber){ publisher.publishEvent(new SalesOrderEvent.Backordered(soNumber)); }
    @Override public void publishRejected(String soNumber)   { publisher.publishEvent(new SalesOrderEvent.Rejected(soNumber)); }
    @Override public void publishReceived(String soNumber)   { publisher.publishEvent(new SalesOrderEvent.Received(soNumber)); }
}
