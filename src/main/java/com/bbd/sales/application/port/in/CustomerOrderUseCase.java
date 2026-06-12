package com.bbd.sales.application.port.in;

import com.bbd.sales.application.command.CreateCustomerOrderCommand;
import com.bbd.sales.application.command.SearchCustomerOrderQuery;
import com.bbd.sales.application.command.UpdateCustomerOrderCommand;
import com.bbd.sales.application.result.CustomerOrderResult;
import com.bbd.sales.application.result.CustomerOrderStatusChangeResult;
import com.bbd.sales.application.result.CustomerOrderSummaryResult;
import com.bbd.sales.application.result.SalesOrderPageResult;
import com.bbd.sales.global.security.CurrentUser;

public interface CustomerOrderUseCase {
    CustomerOrderResult create(CreateCustomerOrderCommand command);

    CustomerOrderResult update(UpdateCustomerOrderCommand command);

    CustomerOrderResult get(String coNumber, CurrentUser currentUser);

    SalesOrderPageResult<CustomerOrderSummaryResult> search(SearchCustomerOrderQuery query);

    CustomerOrderStatusChangeResult confirm(String coNumber, CurrentUser currentUser);

    CustomerOrderStatusChangeResult cancel(String coNumber, CurrentUser currentUser);

    CustomerOrderStatusChangeResult close(String coNumber, CurrentUser currentUser);

}
