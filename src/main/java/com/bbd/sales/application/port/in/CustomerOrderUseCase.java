package com.bbd.sales.application.port.in;

import com.bbd.sales.application.command.CreateCustomerOrderCommand;
import com.bbd.sales.application.command.SearchCustomerOrderQuery;
import com.bbd.sales.application.command.UpdateCustomerOrderCommand;
import com.bbd.sales.application.result.CustomerOrderResult;
import com.bbd.sales.application.result.CustomerOrderStatusChangeResult;
import com.bbd.sales.application.result.CustomerOrderSummaryResult;
import com.bbd.sales.application.result.SalesOrderPageResult;

public interface CustomerOrderUseCase {
    CustomerOrderResult create(CreateCustomerOrderCommand command);

    CustomerOrderResult update(UpdateCustomerOrderCommand command);

    CustomerOrderResult get(String coNumber);

    SalesOrderPageResult<CustomerOrderSummaryResult> search(SearchCustomerOrderQuery query);

    CustomerOrderStatusChangeResult confirm(String coNumber);

    CustomerOrderStatusChangeResult cancel(String coNumber);

    CustomerOrderStatusChangeResult close(String coNumber, String idempotencyKey);

}
