package com.bbd.sales.config;

import com.bbd.sales.adapter.out.external.InventoryHttpService;
import com.bbd.sales.adapter.out.external.ItemHttpService;
import com.bbd.sales.adapter.out.external.ProcurementHttpService;
import com.bbd.sales.adapter.out.external.UserHttpService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
//@ImportHttpServices(
//        group = "bbd-user-service",
//        types = UserHttpService.class
//)
@ImportHttpServices(
        group = "bbd-item-service",
        types = ItemHttpService.class
)
//@ImportHttpServices(
//        group = "bbd-inventory-service",
//        types = InventoryHttpService.class
//)
@ImportHttpServices(
        group = "bbd-procurement-service",
        types = ProcurementHttpService.class
)
public class HttpServiceConfig {
}