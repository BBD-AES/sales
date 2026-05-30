package com.bbd.sales.application.port.in;

import com.bbd.sales.application.command.CreateSalesOrderCommand;
import com.bbd.sales.application.command.SearchSalesOrderQuery;
import com.bbd.sales.application.command.UpdateSalesOrderCommand;
import com.bbd.sales.application.result.SalesOrderPageResult;
import com.bbd.sales.application.result.SalesOrderResult;
import com.bbd.sales.application.result.SalesOrderStatusChangeResult;
import com.bbd.sales.application.result.SalesOrderSummaryResult;
import com.bbd.sales.global.security.CurrentUser;

/**
 * 인바운드(구동) 포트 = "이 애플리케이션으로 무엇을 할 수 있는가"의 계약.
 * web 컨트롤러(인바운드 어댑터)는 이 인터페이스에만 의존하고, 구현체는 모른다.
 * 반환 타입은 web 응답 DTO 가 아니라 application 의 Result 다(경계 격리).
 */
public interface SalesOrderUseCase {

    SalesOrderPageResult<SalesOrderSummaryResult> search(SearchSalesOrderQuery query);

    SalesOrderResult create(CreateSalesOrderCommand command);

    SalesOrderResult get(String soNumber, CurrentUser currentUser);

    SalesOrderResult update(UpdateSalesOrderCommand command);

    SalesOrderStatusChangeResult cancel(String soNumber, CurrentUser currentUser);

    SalesOrderStatusChangeResult approve(String soNumber, CurrentUser currentUser);

    SalesOrderStatusChangeResult reject(String soNumber, String reason, CurrentUser currentUser);

    SalesOrderStatusChangeResult receive(String soNumber, CurrentUser currentUser);
}
