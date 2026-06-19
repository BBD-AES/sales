package com.bbd.sales.application.port.in;

import com.bbd.sales.application.command.CreateSalesOrderCommand;
import com.bbd.sales.application.command.SearchSalesOrderQuery;
import com.bbd.sales.application.command.UpdateSalesOrderCommand;
import com.bbd.sales.application.result.SalesOrderPageResult;
import com.bbd.sales.application.result.SalesOrderResult;
import com.bbd.sales.application.result.SalesOrderStatusChangeResult;
import com.bbd.sales.application.result.SalesOrderSummaryResult;

/**
 * 인바운드(구동) 포트 = "이 애플리케이션으로 무엇을 할 수 있는가"의 계약.
 * web 컨트롤러(인바운드 어댑터)는 이 인터페이스에만 의존하고, 구현체는 모른다.
 * 반환 타입은 web 응답 DTO 가 아니라 application 의 Result 다(경계 격리).
 */
public interface SalesOrderUseCase {

    SalesOrderPageResult<SalesOrderSummaryResult> search(SearchSalesOrderQuery query);

    SalesOrderResult create(CreateSalesOrderCommand command);

    SalesOrderResult get(String soNumber);

    SalesOrderResult update(UpdateSalesOrderCommand command);

    /** 지점 관리자가 HQ로 제출(REQUESTED -> SUBMITTED). */
    SalesOrderStatusChangeResult submit(String soNumber);

    /** 제출 회수(SUBMITTED -> REQUESTED). 요청자가 수정하려 되돌림. */
    SalesOrderStatusChangeResult withdraw(String soNumber);

    SalesOrderStatusChangeResult cancel(String soNumber);

    /** HQ 승인(SUBMITTED -> IN_FULFILLMENT, 재고 부족 시 BACKORDERED). */
    SalesOrderStatusChangeResult approve(String soNumber);

    /** PO 입고 후 백오더 해소(BACKORDERED -> IN_FULFILLMENT). */
    SalesOrderStatusChangeResult fulfillBackorder(String soNumber);

    SalesOrderStatusChangeResult reject(String soNumber, String reason);

    SalesOrderStatusChangeResult receive(String soNumber);
}
