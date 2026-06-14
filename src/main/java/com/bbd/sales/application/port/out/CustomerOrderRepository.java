package com.bbd.sales.application.port.out;

import com.bbd.sales.domain.CustomerOrder;

import java.util.Optional;

//
public interface CustomerOrderRepository {
    String nextCoNumber();

    CustomerOrder save(CustomerOrder customerOrder);

    Optional<CustomerOrder> findByCoNumber(String coNumber);

    CustomerOrderPage search(CustomerOrderSearchCriteria criteria, int page, int size);
}
